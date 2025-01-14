package run_ffmpeg;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Logger;

public class MatchArchives
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_match_archives.txt" ;

	private final String tvShowsOutputFileName = "match_tv_shows.csv" ;
	private final String moviesOutputFileName = "match_movies.csv" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	//	private static final String stopFileName = "C:\\Temp\\stop_ocr_subtitle.txt" ;

	protected Vector< String > myTVShows = new Vector< String >() ;
	protected Vector< String > myMovies = new Vector< String >() ;
	protected Vector< String > otherTVShows = new Vector< String >() ;
	protected Vector< String > otherMovies = new Vector< String >() ;

	protected String[] myTVShowLocations = {
			"\\\\yoda\\MP4\\TV Shows",
			"\\\\skywalker\\Media\\MP4\\TV_Shows"
	} ;
	protected String[] myMovieLocations = {
			"\\\\yoda\\MP4\\Movies",
			"\\\\skywalker\\Media\\MP4\\Movies"
	} ;
	protected String[] otherTVShowLocations = {
			"\\\\skywalker\\usbshare1-2\\TV"
	} ;
	protected String[] otherMovieLocations = {
			"\\\\skywalker\\usbshare1-2\\Movies"
	} ;

	public static void main( String[] args )
	{
		(new MatchArchives()).run() ;		
	}

	public MatchArchives()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public void run()
	{
		for( String location : myTVShowLocations )
		{
			log.info( "Scanning location " + location + " ..." ) ;
			myTVShows.addAll( getFolders( location ) ) ;
		}
		log.info( "Found " + myTVShows.size() + " myTVShows" ) ;

		for( String location : otherTVShowLocations )
		{
			log.info( "Scanning location " + location + " ..." ) ;
			otherTVShows.addAll( getFolders( location ) ) ;
		}
		log.info( "Found " + otherTVShows.size() + " otherTVShows" ) ;

		for( String location : myMovieLocations )
		{
			log.info( "Scanning location " + location + " ..." ) ;
			myMovies.addAll( getFolders( location ) ) ;
		}
		log.info( "Found " + myMovies.size() + " myMovies" ) ;

		for( String location : otherMovieLocations )
		{
			log.info( "Scanning location " + location + " ..." ) ;
			otherMovies.addAll( getFolders( location ) ) ;
		}
		log.info( "Found " + otherMovies.size() + " otherMovies" ) ;

		log.info( "Sorting archives..." ) ;
		Collections.sort( myTVShows ) ;
		Collections.sort( otherTVShows ) ;
		Collections.sort( myMovies ) ;
		Collections.sort( otherMovies ) ;
		log.info( "Done sorting." ) ;
		
		log.info( "Writing TV Shows CSV file..." ) ;
		writeCSV( tvShowsOutputFileName, myTVShows, otherTVShows ) ;
		log.info( "Done writing TV Shows CSV file." ) ;
		
		log.info( "Writing Movies CSV file..." ) ;
		writeCSV( moviesOutputFileName, myMovies, otherMovies ) ;
		log.info( "Done writing Movies CSV file." ) ;
	}

	protected void writeCSV( final String outputFileName, Vector< String > myArchive, Vector< String > otherArchive )
	{
		Set< String > myArchiveSet = new TreeSet< String >( String.CASE_INSENSITIVE_ORDER ) ;
		Set< String > otherArchiveSet = new TreeSet< String >( otherArchive ) ;
		Set< String > combinedArchiveSet = new TreeSet< String >( String.CASE_INSENSITIVE_ORDER ) ;

		myArchiveSet.addAll( myArchive ) ;
		otherArchiveSet.addAll( otherArchive ) ;
		combinedArchiveSet.addAll( myArchiveSet ) ;
		combinedArchiveSet.addAll( otherArchiveSet ) ;
		
		try
		{
			BufferedWriter writer = new BufferedWriter( new FileWriter( outputFileName ) ) ;

			log.info( "Writing archive..." ) ;
//			writer.write( "My Archive;Other Archive" + System.lineSeparator() ) ;

			for( String theKey : combinedArchiveSet )
			{
				if( myArchiveSet.contains( theKey ) )
				{
					writer.write( theKey ) ;
				}
				writer.write( "," ) ;
				if( otherArchiveSet.contains( theKey ) )
				{
					writer.write( theKey ) ;
				}
				writer.write( System.lineSeparator() ) ;
			}

			log.info( "Done writing archive" ) ;
			
			writer.close() ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception writing file: " + theException.toString() ) ;
		}
	}
	
	/**
	 * Return all folders in the given folder.
	 * @return
	 */
	public Vector< String > getFolders( final String location )
	{
		Vector< String > retMe = new Vector< String >() ;
		File directory = new File( location ) ;

		if( !directory.exists() )
		{
			log.warning( "location " + location + " does not exist" ) ;
			return retMe ;
		}

		if( !directory.isDirectory() )
		{
			log.warning( "location " + location + " is not a directory" ) ;
			return retMe ;
		}

		File[] files = directory.listFiles() ;

		if( null == files )
		{
			log.warning( "Empty directory: " + location ) ;
			return retMe ;
		}

		for( File theFile : files )
		{
			if( theFile.isDirectory() )
			{
				log.fine( "Found folder: " + theFile.toString() ) ;
				
				final String path = theFile.getAbsolutePath() ;
				if( !path.contains( "(" ) )
				{
					log.info( "Missing year: " + theFile.getAbsolutePath() ) ;
				}
				int lastSpaceIndex = path.lastIndexOf( "\\" ) ;

				if( lastSpaceIndex != -1 )
				{
					String lastToken = path.substring( lastSpaceIndex + 1 ) ;
					//		            log.info( "Last token: " + lastToken ) ;
					retMe.add( lastToken ) ;
				}
			} // if( isDirectory() )
		} // for( theFile )
		return retMe ;
	}
}
