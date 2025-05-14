package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
public class ProbeDirectories extends run_ffmpegControllerThreadTemplate< ProbeDirectoriesWorkerThread >
{
	/// This map will store all of the FFmpegProbeResults in the probeInfoCollection, keyed by the long path to the document.
	/// The probeInfoMap is thread safe.
	private transient Map< String, FFmpegProbeResult > probeInfoMap = new ConcurrentHashMap< String, FFmpegProbeResult >() ;

	private transient List< String > foldersToProbe = new ArrayList< String >() ;
	private transient List< File > filesToProbe = new ArrayList< File >() ;

	/// The number of threads to use
	protected int numThreads = 3 ;

	/// Keep track if the database has been loaded.
	private boolean databaseBeenLoaded = false ;

	/// The extensions of the files to probe herein.
	private final String[] extensionsToProbe =
		{
				".mkv",
				".mp4"
		} ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_probe_directories.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_probe_directories.txt" ;

	/**
	 * The default constructor uses the static file names.
	 */
	public ProbeDirectories()
	{
		super( logFileName, stopFileName ) ;
	}

	/**
	 * This constructor is intended for external users of this class and allows passing names of the files used
	 *  externally.
	 * @param logFileName
	 * @param stopFileName
	 */
	public ProbeDirectories( final String logFileName, final String stopFileName )
	{
		super( logFileName, stopFileName ) ;
	}

	public ProbeDirectories( Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpegProbeResult > probeInfoCollection )
	{
		super( log, common, stopFileName, masMDB, probeInfoCollection ) ;
	}

	public static void main(String[] args)
	{
		ProbeDirectories probeDirectory = new ProbeDirectories() ;
		probeDirectory.Init() ;
		probeDirectory.Execute() ;
		System.out.println( "Process shut down." ) ;
	}

	/**
	 * Override the method called when execution begins.
	 */
	@Override
	public void Init()
	{
		setUseThreads( true ) ;
		common.setTestMode( false ) ;
		foldersToProbe.addAll( Common.getAllMediaFolders() ) ;
		//foldersToProbe.add( "\\\\skywalker\\Media\\Staging\\Movies" ) ;
		loadProbeInfoDatabase() ;

		// Retrieve all of the files to probe.
		for( String folderToProbe : foldersToProbe )
		{
			if( !shouldKeepRunning() )
			{
				// Stop running
				log.info( "Shutting down thread" ) ;
				break ;
			}
			log.info( "Loading files from " + folderToProbe + "..." ) ;

			final List< File > filesToProbeInThisFolder = common.getFilesInDirectoryByExtension( folderToProbe, extensionsToProbe ) ;
			filesToProbe.addAll( filesToProbeInThisFolder ) ;

			log.info( "Probing " + filesToProbeInThisFolder.size() + " file(s) in folder " + folderToProbe ) ;

		} // for( folderToProbe )
		log.info( "Will probe " + filesToProbe.size() + " file(s)" ) ;

		// Check for missing files, but only if we're looking at the core media files
		boolean doCheckForMissingFiles = true ;
		for( String folderToProbe : foldersToProbe )
		{
			if( !findMatch( folderToProbe, Common.getAllMediaFolders() ) )
			{
				// Not found, skip the check for missing files.
				doCheckForMissingFiles = false ;
				break ;
			}
		}

		if( doCheckForMissingFiles )
		{
			final long startTime = System.nanoTime() ;
			log.info( getName() + " Checking for missing files" ) ;

			checkForMissingFiles( filesToProbe ) ;

			final long endTime = System.nanoTime() ;
			log.info( getName() + " Finished checking for missing files " + foldersToProbe.toString()
			+ ", " + common.makeElapsedTimeString( startTime, endTime ) ) ;
		}
	}

	public boolean findMatch( final String findMe, final List< String > searchList )
	{
		for( String searchListItem : searchList )
		{
			if( searchListItem.equals( findMe ) )
			{
				return true ;
			}
		}
		return false ;
	}

	/**
	 * Build the worker threads for this instance.
	 */
	@Override
	protected List< ProbeDirectoriesWorkerThread > buildWorkerThreads()
	{
		List< ProbeDirectoriesWorkerThread > threads = new ArrayList< ProbeDirectoriesWorkerThread >() ;

		for( int i = 0 ; i < getNumThreads() ; ++i )
		{
			ProbeDirectoriesWorkerThread pdwt = new ProbeDirectoriesWorkerThread( this,
					log,
					common,
					probeInfoCollection,
					probeInfoMap ) ;
			pdwt.setName( "PDWT #" + i ) ;
			threads.add( pdwt ) ;
		}

		return threads ;
	}

	public synchronized File getNextFileToProbe()
	{
		File probeMe = null ;
		synchronized( filesToProbe )
		{
			if( !filesToProbe.isEmpty() )
			{
				probeMe = filesToProbe.removeFirst() ;
			}
		}
		return probeMe ;
	}

	/**
	 * Search through the object's probeInfoMap and include those items whose long path prefixes start
	 *  with each of the entries in the driveAndFoldersToProbe.
	 * @param foldersToProbe
	 * @return
	 */
	public Map< String, FFmpegProbeResult > getProbeInfoForFolders( final List< String > foldersToProbe )
	{
		Map< String, FFmpegProbeResult > returnMeMap = new HashMap< String, FFmpegProbeResult >() ;
		for( String folderToProbe : foldersToProbe )
		{
			// Ensure the trailing \\ is included so \\\\skywalker\\Media doesn't also pick up all entries for \\\\yoda\\MP4_2
			final String folderToProbeSearch = common.addPathSeparatorIfNecessary( folderToProbe ) ;

			// Walk through the probeInfoMap to search for long path prefixes.
			// Can't call get() here because it doesn't have a way to search for startsWith()
			// (Yes I could add a comparator but I prefer to keep the code clean and easier to debug.)
			for( Map.Entry< String, FFmpegProbeResult > entry : probeInfoMap.entrySet() )
			{
				final String longPath = entry.getKey() ;
				final FFmpegProbeResult theProbeResult = entry.getValue() ;
				if( longPath.startsWith( folderToProbeSearch ) )
				{
					returnMeMap.put( theProbeResult.getFileNameWithPath(), theProbeResult ) ;
				}
			}
		}
		return returnMeMap ;
	}

	/**
	 * Load the entire probeInfoCollection into a location in memory for rapid use.
	 */
	private void loadProbeInfoDatabase()
	{
		if( hasDatabaseBeenLoaded() )
		{
			// Already loaded. Nothing to do here.
			return ;
		}

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
		} // while( probe....hasNext() )
		setDatabaseBeenLoaded( true ) ;
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
	 * @param fileToProbe
	 * @param forceRefresh Set to true to force the file to be re-probed and update the database.
	 * @return
	 */
	public FFmpegProbeResult probeFileAndUpdateDB( final File fileToProbe, boolean forceRefresh )
	{
		assert( fileToProbe != null ) ;

		// Make sure the database has been loaded.
		if( !hasDatabaseBeenLoaded() )
		{
			// Load the entire probeInfo database into the probeInfoMap
			loadProbeInfoDatabase() ;
		}

		// Lookup the file in the probeInfoMap
		FFmpegProbeResult theProbeResult = probeInfoMap.get( fileToProbe.getAbsolutePath() ) ;
		//probeInfoCollection.find( Filters.eq( "fileNameWithPath", fileToProbe.getAbsolutePath() ) ).first() ;
		// theProbeResult may be null if the file has not yet been probed.
		if( null == theProbeResult )
		{
			// No entry found for this file.
			// Probe it and add to the database
			theProbeResult = common.ffprobeFile( fileToProbe, log ) ;

			probeInfoMap.put( fileToProbe.getAbsolutePath(), theProbeResult ) ;
			synchronized( probeInfoCollection )
			{
				try
				{
					probeInfoCollection.insertOne( theProbeResult ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Exception during insertOne: " + theException.toString() ) ;
				}
			}
		}
		return theProbeResult ;
	}

	public boolean hasDatabaseBeenLoaded()
	{
		return databaseBeenLoaded ;
	}

	public void setDatabaseBeenLoaded( boolean databaseBeenLoaded )
	{
		this.databaseBeenLoaded = databaseBeenLoaded ;
	}

	public void setNumThreads( final int numThreads )
	{
		this.numThreads = numThreads ;
	}

	public int getNumThreads()
	{
		return numThreads ;
	}

	/**
	 * Check all existing probeInfo records against files in the file system. Remove
	 * any database entries that do not correlate to files that exist.
	 */
	protected void checkForMissingFiles( final List< File > foundFiles )
	{
		log.info( getName() + " Found " + foundFiles.size() + " file(s)" ) ;

		// Need to walk through the probeInfoMap and check if each entry corresponds to an active file.
		// Will need to be able to search the files by path -- put the foundFiles into a map.
		Map< String, File > fileSystemMap = new HashMap< String, File >() ;
		for( File theFile : foundFiles )
		{
			if( !shouldKeepRunning() )
			{
				return ;
			}
			fileSystemMap.put( theFile.getAbsolutePath(), theFile ) ;
		}

		// Now walk through the probeInfoMap to search for missing files.
		for( Map.Entry< String, FFmpegProbeResult > entry : probeInfoMap.entrySet() )
		{
			if( !shouldKeepRunning() )
			{
				return ;
			}

			final String absolutePath = entry.getKey() ;
			final FFmpegProbeResult theProbeResult = entry.getValue() ;

			// Find the file in the fileSystemMap
			final File theFile = fileSystemMap.get( absolutePath ) ;
			if( null == theFile )
			{
				log.info( getName() + " Deleting missing file from database: " + absolutePath ) ;
				if( !common.getTestMode() )
				{
					synchronized( probeInfoCollection )
					{
						try
						{
							probeInfoCollection.deleteOne( Filters.eq( "_id", theProbeResult._id ) ) ;
						}
						catch( Exception theException )
						{
							log.warning( "Exception: " + theException.toString() ) ;
						}
					}
				}
			}
		}
	}
}
