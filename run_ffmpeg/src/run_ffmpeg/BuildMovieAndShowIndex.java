package run_ffmpeg;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;
import org.bson.conversions.Bson;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * The purpose of this class is to query the database for individual files and
 *  correlate those to individual movies and tv shows. Each of those will be
 *  added/updated in the database to support future activities (matching,
 *  identifying missing files, statistics, work actions, etc.).
 * Database collections:
 *  ProbeInfo: One entry for each mkv or mp4 file. Includes the FFmpegProbeResult for each.
 *  MovieAndShowInfo: Information about a single movie or TV show. Includes attached MovieAndShowInfo
 *   entry for each. Each MovieAndShowInfo includes a list of known mp4 and mkv files.
 * @author Dan
 *
 */
public class BuildMovieAndShowIndex
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "build_movie_and_show_index.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_build_movie_and_show_index.txt" ;
	
	public BuildMovieAndShowIndex()
	{
		log = Common.setupLogger( logFileName, BuildMovieAndShowIndex.class.getName() ) ;
	}
	
	public static void main( String[] args )
	{
		BuildMovieAndShowIndex bmasi = new BuildMovieAndShowIndex() ;
		bmasi.buildMovieIndex() ;
	}
	
	private void buildMovieIndex()
	{
		Map< String, MovieAndShowInfo > movieMap = new HashMap< String, MovieAndShowInfo >() ;
		Map< String, MovieAndShowInfo > tvShowMap = new HashMap< String, MovieAndShowInfo >() ;
		
		// Establish connection to the database.
		MoviesAndShowsMongoDB masMDB = new MoviesAndShowsMongoDB() ;
		MongoCollection< FFmpegProbeResult > probeInfoCollection = masMDB.getProbeInfoCollection() ;
		MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		
		// First, let's pull the mkv file info
		Bson mp4A = Filters.regex( "filename", ".*" ) ;
		log.info( "Running find..." ) ;
		FindIterable< FFmpegProbeResult > probeInfoFindResult = probeInfoCollection.find( mp4A ) ;

		Iterator< FFmpegProbeResult > probeInfoFindResultIterator = probeInfoFindResult.iterator() ;
		while( probeInfoFindResultIterator.hasNext() )
		{
			FFmpegProbeResult probeResult = probeInfoFindResultIterator.next() ;
			File theFile = new File( probeResult.getFilename() ) ;
			if( theFile.getParent().contains( "Season " ) )
			{
				// TV Show
				// TV show names will be stored by combining the name of the show with the season
				// For example: "Californication_Season 01"
				final String tvShowName = theFile.getParentFile().getParentFile().getName()
						+ "_"
						+ theFile.getParentFile().getName() ;
				log.info( "Found TV show: " + tvShowName + ", filename: " + theFile.getName() ) ;
				
				addEntryToMap( tvShowMap, tvShowName, probeResult, theFile ) ;
			}
			else if( theFile.getParent().contains( "(" ) )
			{
				// Movie
				// The formal should be like this:
				// \\yoda\Backup\Movies\Transformers (2007)\Making Of-behindthescenes.mkv
				final String movieName = theFile.getParentFile().getName() ;
				log.info( "Found movie: " + movieName ) ;
				// movieName should be of the form "Transformers (2007)"
				addEntryToMap( movieMap, movieName, probeResult, theFile ) ;
			}
			else
			{
				log.warning( "Parse error for file: " + theFile.getAbsolutePath() ) ;
			}
		} // while( iterator.hasNext() )
		
		// Done reading the mkv and mp4 files from the database.
		// Sort through each movie and tv show to find matching filenames.
		correlateFiles( movieMap ) ;
		correlateFiles( tvShowMap ) ;
		
//		mkvProbeInfoFindIterator.forEachRemaining( entry -> log.info( "BuildMovieAndShowIndex> entry: " + entry.getFilename() ) ) ;
		log.info( "Shutting down." ) ;
	}
	
	private void correlateFiles( Map< String, MovieAndShowInfo > probeMap )
	{
		// Iterate through all items in the probeMap and invoke each correlation method
		// Note that the items in the probeMap should be unique
		for( Map.Entry< String, MovieAndShowInfo > set : probeMap.entrySet() )
		{
			String movieOrShowName = set.getKey() ;
			MovieAndShowInfo movieAndShowInfo = set.getValue() ;
			movieAndShowInfo.correlateProbeResults() ;
		}
	}
	
	/**
	 * Place the tv show or movie file into the given storageMap. This could be either a tv show entry or movie entry.
	 * @param storageMap: Place to store the movie or tv show
	 * @param movieOrTVShowName: The name of the movie or tv show. Will always be non-null
	 * @param tvShowSeasonName: Null if the entry is a movie; a season name if tv.
	 * @param theFile
	 */
	private void addEntryToMap( Map< String, MovieAndShowInfo > storageMap,
			final String movieOrTVShowName,
			final FFmpegProbeResult probeResult,
			final File theFile )
	{
		boolean isMP4 = theFile.getName().contains( ".mp4" ) ? true : false ;

		MovieAndShowInfo mapEntry = storageMap.get( movieOrTVShowName ) ;
		if( null == mapEntry )
		{
			// Not already in the storageMap
			mapEntry = new MovieAndShowInfo( movieOrTVShowName, log ) ;
			storageMap.put( movieOrTVShowName, mapEntry ) ;
		}
		// Post condition: theShow is non-null and exists in the storageMap.
		if( isMP4 )
		{
			mapEntry.addMP4File( probeResult ) ;
		}
		else
		{
			mapEntry.addMKVFile( probeResult ) ;
		}
	}
	
}
