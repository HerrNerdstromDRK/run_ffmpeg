package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bson.conversions.Bson;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * Run ffprobe on each file in the probe directories array and record that information
 *  into the probe database.
 * @author Dan
 */
public class ProbeDirectories
{
	/// Setup the logging subsystem
	private transient Logger log = null ;
	private transient Common common = null ;

	private transient MoviesAndShowsMongoDB masMDB = null ;
	private transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	/// This map will store all of the FFmpegProbeResults in the probeInfoCollection, keyed by the long path to the document.
	private transient Map< String, FFmpegProbeResult > probeInfoMap = new HashMap< String, FFmpegProbeResult >() ;

	/// The structure that contains all worker threads, indexed by the drive being scanned.
	private transient Map< String, ProbeDirectoriesWorkerThread > threadMap =
			Collections.synchronizedMap( new HashMap< String, ProbeDirectoriesWorkerThread >() ) ;

	private boolean useThreads = true ;
	private boolean keepRunning = true ;
	private final String singleThreadedName = "Single thread" ;

	/// Store the drives to probe.
	/// By default this will be all mp4 and mkv drives.
	private List< String > drivesToProbe = new ArrayList< String >() ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_probe_directories.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_probe_directories.txt" ;

	public ProbeDirectories()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		masMDB = new MoviesAndShowsMongoDB( log ) ;
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
	}

	public ProbeDirectories( Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpegProbeResult > probeInfoCollection )
	{
		assert( log != null ) ;
		assert( common != null ) ;
		assert( masMDB != null ) ;
		assert( probeInfoCollection != null ) ;

		this.log = log ;
		this.common = common ;
		this.masMDB = masMDB ;
		this.probeInfoCollection = probeInfoCollection ;
	}

	public static void main(String[] args)
	{
		ProbeDirectories probeDirectory = new ProbeDirectories() ;
		probeDirectory.execute() ;
		System.out.println( "Process shut down." ) ;
	}

	/**
	 * Execute as the controller thread.
	 */
	public void execute()
	{
		setUseThreads( true ) ;
		loadProbeInfoDatabase() ;
		drivesToProbe.addAll( common.getAllMKVDrives() ) ;
		drivesToProbe.addAll( common.getAllMP4Drives() ) ;
		buildWorkerThreads() ;
		startThreads() ;

		if( !isUseThreads() )
		{
			// Single threaded.
			// The threadMap should have a single object inside it -- call its run method.
			assert( 1 == threadMap.size() ) ;
			ProbeDirectoriesWorkerThread pdwt = threadMap.get( getSingleThreadedName() ) ;
			assert( pdwt != null ) ;

			// Run the probe method.
			pdwt.run() ;
		}
		else
		{
			// Using threads
			// Just wait for the threads to complete or the shutdown command to be issued.
			while( shouldKeepRunning() && atLeastOneThreadIsAlive() )
			{
				try
				{
					Thread.sleep( 100 ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Error in sleep: " + theException.toString() ) ;
				}
			}
		}

		log.info( "Shutting down" ) ;
		stopThreads() ;
		joinThreads() ;
	}

	protected boolean atLeastOneThreadIsAlive()
	{
		if( !isUseThreads() )
		{
			return true ;
		}

		for( Map.Entry< String, ProbeDirectoriesWorkerThread > entry : threadMap.entrySet() )
		{
			ProbeDirectoriesWorkerThread pdwt = entry.getValue() ;

			if( pdwt.isAlive() )
			{
				return true ;
			}
		}
		return false ;
	}

	/**
	 * Build the worker threads for this instance.
	 * Each thread will only receive the portion of the probeInfoMap related to its drive.
	 * Note that this means the drive prefixes must be mutually exclusive.
	 */
	protected void buildWorkerThreads()
	{
		assert( !drivesToProbe.isEmpty() ) ;

		if( isUseThreads() )
		{
			// For now, build a thread for each entry in the drivesToProbe list.
			// May need to change this later depending on any issues with the file servers.
			for( String driveToProbe : drivesToProbe )
			{
				// Add the folder to probe for this drive.
				List< String > driveAndFoldersToProbe = common.addMoviesAndTVShowFoldersToDrive( driveToProbe ) ;
				Map< String, FFmpegProbeResult > threadProbeInfoMap = getProbeInfoForDrives( driveAndFoldersToProbe ) ; 

				ProbeDirectoriesWorkerThread pdwt = new ProbeDirectoriesWorkerThread( this,
						log,
						common,
						//						masMDB,
						probeInfoCollection,
						threadProbeInfoMap,
						driveAndFoldersToProbe ) ;
				pdwt.setName( driveToProbe ) ;
				threadMap.put( driveToProbe, pdwt ) ;
			}
		}
		else
		{
			// Use a single thread. This is done by creating a single worker thread instance and passing it the entire list of
			// drives and folders, and letting the main loop execute it directly. The worker thread instance will not be started
			//  -- it will be run directly by the main loop here.
			List< String > driveAndFoldersToProbe = common.addMoviesAndTVShowFoldersToEachDrive( drivesToProbe ) ;

			ProbeDirectoriesWorkerThread pdwt = new ProbeDirectoriesWorkerThread( this,
					log,
					common,
					//					masMDB,
					probeInfoCollection,
					probeInfoMap,
					driveAndFoldersToProbe ) ;

			threadMap.put( getSingleThreadedName(), pdwt ) ; 
		}
	}

	/**
	 * Search through the object's probeInfoMap and include those items whose long path prefixes start
	 *  with each of the entries in the driveAndFoldersToProbe.
	 * @param driveAndFoldersToProbe
	 * @return
	 */
	public Map< String, FFmpegProbeResult > getProbeInfoForDrives( final List< String > driveAndFoldersToProbe )
	{
		Map< String, FFmpegProbeResult > returnMeMap = new HashMap< String, FFmpegProbeResult >() ;
		for( String driveOrFolderToProbe : driveAndFoldersToProbe )
		{
			// Ensure the trailing \\ is included so \\\\yoda\\MP4 doesn't also pick up all entries for \\\\yoda\\MP4_3
			final String driveOrFolderToProbeSearch = common.addPathSeparatorIfNecessary( driveOrFolderToProbe ) ;
	
			// Walk through the probeInfoMap to search for long path prefixes.
			// Can't call get() here because it doesn't have a way to search for startsWith()
			for( Map.Entry< String, FFmpegProbeResult > entry : probeInfoMap.entrySet() )
			{
				String longPath = entry.getKey() ;
				FFmpegProbeResult theProbeResult = entry.getValue() ;
				if( longPath.startsWith( driveOrFolderToProbeSearch ) )
				{
					returnMeMap.put( theProbeResult.getFileNameWithPath(), theProbeResult ) ;
				}
			}
		}
		return returnMeMap ;
	}

	public String getSingleThreadedName()
	{
		return singleThreadedName ;
	}

	public boolean isUseThreads()
	{
		return useThreads ;
	}

	protected void joinThreads()
	{
		log.info( "Joining threads." ) ;
		if( isUseThreads() )
		{
			for( Map.Entry< String, ProbeDirectoriesWorkerThread > entry : threadMap.entrySet() )
			{
				final String key = entry.getKey() ;
				ProbeDirectoriesWorkerThread pdwt = entry.getValue() ;

				log.info( "Joining thread " + key ) ;
				try
				{
					pdwt.join() ;
					log.fine( "Joined thread " + key ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Error joining thread " + key + ": " + theException.toString() ) ;
				}
			}
		}
	}

	/**
	 * Load the entire probeInfoCollection into a location in memory for rapid use.
	 */
	private void loadProbeInfoDatabase()
	{
		log.info( "Loading probe info collection" ) ;
		Bson findFilesFilter = Filters.regex( "fileNameWithPath", ".*" ) ;
		FindIterable< FFmpegProbeResult > probeInfoFindResult = probeInfoCollection.find( findFilesFilter ) ;

		Iterator< FFmpegProbeResult > probeInfoFindResultIterator = probeInfoFindResult.iterator() ;

		// This loop stores all FFmpegProbeResults in a single structure
		while( probeInfoFindResultIterator.hasNext() )
		{
			FFmpegProbeResult probeResult = probeInfoFindResultIterator.next() ;
			final String pathToFile = probeResult.getFileNameWithPath() ;

			// Store the FFmpegProbeResult
			probeInfoMap.put( pathToFile, probeResult ) ;
		} // while( probe....hasNext()
		log.info( "Retrieved " + probeInfoMap.size() + " probe entry/entries" ) ;
	}

	/**
	 * Convenience method for external users of objects of this class.
	 * Note that the fully featured instance of this class will read the entire probe info collection into memory,
	 *  and then walk through the collection for a full-database comparison. However, what we need here is an efficient
	 *  method to probe a single file. To that end, use a database call.
	 * @param fileToProbe
	 * @return
	 */
	public FFmpegProbeResult probeFileAndUpdateDB( final File fileToProbe )
	{
		return probeFileAndUpdateDB( fileToProbe, false ) ;
	}

	/**
	 * Convenience method for external users of objects of this class.
	 * Note that the fully featured instance of this class will read the entire probe info collection into memory,
	 *  and then walk through the collection for a full-database comparison. However, what we need here is an efficient
	 *  method to probe a single file. To that end, use a database call.
	 * @param fileToProbe
	 * @param forceRefresh Set to true to force the file to be re-probed and update the database.
	 * @return
	 */
	public FFmpegProbeResult probeFileAndUpdateDB( final File fileToProbe, boolean forceRefresh )
	{
		assert( fileToProbe != null ) ;

		// Lookup the file in the probeInfoCollection
		FFmpegProbeResult theProbeResult = probeInfoCollection.find( Filters.eq( "fileNameWithPath", fileToProbe.getAbsoluteFile() ) ).first() ;
		// theProbeResult may be null if the file has not yet been probed.
		if( theProbeResult != null )
		{
			// Found an entry. Add it to the probeInfoMap and pass to the worker thread method.
			probeInfoMap.put( theProbeResult.getFileNameWithPath(), theProbeResult ) ;
		}

		// Create an instance of a PDWT to call its probeFileAndUpdateDB() method.
		ProbeDirectoriesWorkerThread pdwt = new ProbeDirectoriesWorkerThread( this,
				log,
				common,
				//				masMDB,
				probeInfoCollection,
				probeInfoMap,
				drivesToProbe ) ;
		theProbeResult = pdwt.probeFileAndUpdateDB( fileToProbe, forceRefresh ) ;
		return theProbeResult ;
	}

	public void setUseThreads( boolean useThreads )
	{
		this.useThreads = useThreads ;
	}

	public boolean shouldKeepRunning()
	{
		return (!common.shouldStopExecution( stopFileName ) || !keepRunning) ;
	}

	protected void startThreads()
	{
		if( isUseThreads() )
		{
			log.info( "Starting threads." ) ;
			for( Map.Entry< String, ProbeDirectoriesWorkerThread > entry : threadMap.entrySet() )
			{
				final String key = entry.getKey() ;
				ProbeDirectoriesWorkerThread pdwt = entry.getValue() ;

				log.info( "Starting thread " + key ) ;
				pdwt.start() ;
			}
		}
	}

	public void stopRunning()
	{
		keepRunning = false ;
	}

	protected void stopThreads()
	{
		stopRunning() ;
	}
}
