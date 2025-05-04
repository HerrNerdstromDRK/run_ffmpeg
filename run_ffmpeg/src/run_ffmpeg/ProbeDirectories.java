package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
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
public class ProbeDirectories extends run_ffmpegControllerThreadTemplate< ProbeDirectoriesWorkerThread >
{
	/// This map will store all of the FFmpegProbeResults in the probeInfoCollection, keyed by the long path to the document.
	private transient Map< String, FFmpegProbeResult > probeInfoMap = new HashMap< String, FFmpegProbeResult >() ;
	
	private transient List< String > foldersToProbe = new ArrayList< String >() ;

	/// Keep track if the database has been loaded.
	private boolean databaseBeenLoaded = false ;

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
		setUseThreads( false ) ;
		common.setTestMode( false ) ;
		foldersToProbe.addAll( Common.getAllMediaFolders() ) ;
		loadProbeInfoDatabase() ;
	}

	/**
	 * Build the worker threads for this instance.
	 * Each thread will only receive the portion of the probeInfoMap related to its drive.
	 * Note that this means the drive prefixes must be mutually exclusive.
	 */
	@Override
	protected List< ProbeDirectoriesWorkerThread > buildWorkerThreads()
	{
		List< ProbeDirectoriesWorkerThread > threads = new ArrayList< ProbeDirectoriesWorkerThread >() ;

		ProbeDirectoriesWorkerThread pdwt = new ProbeDirectoriesWorkerThread( this,
				log,
				common,
				probeInfoCollection,
				probeInfoMap,
				foldersToProbe ) ;
		pdwt.setName( getSingleThreadedName() ) ;
		threads.add( pdwt ) ;

		return threads ;
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

		// Make sure the database has been loaded.
		if( !hasDatabaseBeenLoaded() )
		{
			loadProbeInfoDatabase() ;
		}

		// Lookup the file in the probeInfoCollection
		FFmpegProbeResult theProbeResult = probeInfoCollection.find( Filters.eq( "fileNameWithPath", fileToProbe.getAbsolutePath() ) ).first() ;
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
				probeInfoCollection,
				probeInfoMap,
				Common.getAllMediaFolders() ) ;
		theProbeResult = pdwt.probeFileAndUpdateDB( fileToProbe, forceRefresh ) ;
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
}
