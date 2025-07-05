package run_ffmpeg;

import java.io.File;
import java.io.FileFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class will check the database and file structures for duplication, integrity, missing files, etc.
 */
public class CheckLogicalIntegrity
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_check_logical_integrity.txt" ;

//	private MoviesAndShowsMongoDB masMDB = null ;
	//	private MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;
	//	private MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;
	//	private MongoCollection< HDorSDFile > hDMoviesAndShowsCollection = null ;
	//	private MongoCollection< HDorSDFile > sDMoviesAndShowsCollection = null ;

	/// This map will store all of the FFmpegProbeResults in the probeInfoCollection, keyed by the long path to the document.
//	private Map< String, FFmpeg_ProbeResult > probeInfoMap = null ;

	/// Store all MovieAndShowInfos stored in the database.
//	private Map< String, MovieAndShowInfo > movieAndShowInfoMap = new HashMap< String, MovieAndShowInfo >() ;

	public CheckLogicalIntegrity()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		initObject() ;
	}

	public CheckLogicalIntegrity( Common common, Logger log )
	{
		this.log = log ;

		initObject() ;
	}

	private void initObject()
	{
		// Establish connection to the database.
//		masMDB = new MoviesAndShowsMongoDB() ;

		// Retrieve the db collections.
		//		probeInfoCollection = masMDB.getProbeInfoCollection() ;
		//		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		//		hDMoviesAndShowsCollection = masMDB.getHDMoviesAndShowsCollection() ;
		//		sDMoviesAndShowsCollection = masMDB.getSDMoviesAndShowsCollection() ;

		loadDatabaseInformation() ;
	}

	public static void main( String[] args )
	{
		CheckLogicalIntegrity cli = new CheckLogicalIntegrity() ;
		cli.execute() ;
		System.out.println( "CheckLogicalIntegrity> Shutdown." ) ;
	}

	public void execute()
	{
		Map< File, List< File > > movieFilesInFolders = getMovieVideoFiles() ;
		log.info( "Got " + movieFilesInFolders.size() + " movie directories" ) ;
		
		log.info( "Checking for missing movie files..." ) ;
		checkForMissingMovieFiles( movieFilesInFolders ) ;
		
		log.info( "Checking for empty folders..." ) ;
		checkForEmptyFolders() ;
		
		log.info( "Complete." ) ; 
	}
	
	public void checkForDuplicatePaths( Map< String, Set< String > > theMap )
	{
		for( Map.Entry< String, Set< String > > entry : theMap.entrySet() )
		{
			final String key = entry.getKey() ;
			final Set< String > value = entry.getValue() ;
	
			if( 0 == value.size() )
			{
				log.warning( "Found empty set for key: " + key ) ;
			}
			else if( value.size() > 1 )
			{
				log.info( "Found duplicates for key " + key + ": " + value.toString() ) ;
			}
		}
	}

	/**
	 * Walk through the media directory structures and report any empty folders. This is used to identify
	 * any shows/movies that may have been moved without updating the database.
	 */
	public void checkForEmptyFolders()
	{
		FileFilter emptyDirectoryFileFilter = new FileFilter()
		{
			public boolean accept( File dir )
			{          
				if( dir.isDirectory() && (0 == dir.list().length) )
				{
					return true ;
				}
				else
				{
					return false ;
				}
			}
		};
	
		List< String > allFolders = Common.getAllMediaFolders() ;
		for( String theFolder : allFolders )
		{
			log.info( "Checking for empty folders in: " + theFolder ) ;
	
			final File theFolderFile = new File( theFolder ) ;			
			final File[] filterDirectoryList = theFolderFile.listFiles( emptyDirectoryFileFilter ) ;
			if( filterDirectoryList != null )
			{
				for( File theFile : filterDirectoryList )
				{
					log.info( "Found empty folder: " + theFile.getAbsolutePath() ) ;
				}
			}
		}
	}

	/**
	 * Check the list of movie files for each folder and verify that each folder has a valid movie file.
	 * This assumes the movieFilesInFolders contains only video files (mkv/mp4, no srt, etc.).
	 * @param movieFilesInFolders
	 */
	public void checkForMissingMovieFiles( final Map< File, List< File > > movieFilesInFolders )
	{
		final Pattern movieFileNamePattern = Pattern.compile( "(?<movieName>.*)\\((?<year>[\\d]{4})\\).*\\.(?<extension>(mkv|mp4))" ) ;
		
		for( Map.Entry< File, List< File > > entry : movieFilesInFolders.entrySet() )
		{
			final File directory = entry.getKey() ;
			final List< File > files = entry.getValue() ;
			
			// Look through each file in this folder for a movie file
			int numMovieFilesFound = 0 ;
			for( File theFile : files )
			{
				final Matcher fileNameMatcher = movieFileNamePattern.matcher( theFile.getName() ) ;
				if( fileNameMatcher.find() )
				{
					// A movie file.
					++numMovieFilesFound ;
				}
			}
			
			if( numMovieFilesFound != 1 )
			{
				log.info( "Found " + numMovieFilesFound + " movie file(s) in folder " + directory.getAbsolutePath() ) ;
			}
		}
	}
	
	Map< File, List< File > > getMovieVideoFiles()
	{
		Map< File, List< File > > movieVideoFiles = new HashMap< File, List< File > >() ;
		
		final String movieFilesTopDirectoryString = Common.getPathToMovies() ;
		final File movieFilesTopDirectoryFile = new File( movieFilesTopDirectoryString ) ;
		if( !movieFilesTopDirectoryFile.exists() || !movieFilesTopDirectoryFile.isDirectory() )
		{
			log.warning( "Doesn't exist or isn't a file: " + movieFilesTopDirectoryString ) ;
			return movieVideoFiles ;
		}
		
		final File[] movieDirectories = movieFilesTopDirectoryFile.listFiles() ;
		for( File movieDirectory : movieDirectories )
		{
			assert( movieDirectory != null ) ;
			if( !movieDirectory.isDirectory() )
			{
				log.warning( "Not a directory: " + movieDirectory.getAbsolutePath() ) ;
				continue ;
			}
			
			final List< File > filesInMovieDirectory = common.getFilesInDirectoryByExtension( movieDirectory.getAbsolutePath(), Common.getVideoExtensions() ) ;
			movieVideoFiles.put( movieDirectory, filesInMovieDirectory ) ;			
		}
		return movieVideoFiles ;
	}

	public void loadDatabaseInformation()
	{
		log.info( "Loading database information.") ;
//		probeInfoMap = masMDB.loadProbeInfoMap() ;
//		loadMovieAndTVMaps() ;

//		movieAndShowInfoMap = masMDB.loadMovieAndShowInfoMap() ;
	}
}
