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

	/// The set of methods and variables for common use.
//	private Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_build_movie_and_show_index.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	//	private static final String stopFileName = "C:\\Temp\\stop_build_movie_and_show_index.txt" ;

	private MoviesAndShowsMongoDB masMDB = null ;
	private MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;
	private MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;
	private MongoCollection< HDorSDFile > hDMoviesAndShowsCollection = null ;
	private MongoCollection< HDorSDFile > sDMoviesAndShowsCollection = null ;

	private Map< String, MovieAndShowInfo > movieMap = new HashMap< String, MovieAndShowInfo >() ;
	private Map< String, MovieAndShowInfo > tvShowMap = new HashMap< String, MovieAndShowInfo >() ;

	public BuildMovieAndShowIndex()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
//		common = new Common( log ) ;

		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB() ;

		// Retrieve the db collections.
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		hDMoviesAndShowsCollection = masMDB.getHDMoviesAndShowsCollection() ;
		sDMoviesAndShowsCollection = masMDB.getSDMoviesAndShowsCollection() ;
	}

	public static void main( String[] args )
	{
		BuildMovieAndShowIndex bmasi = new BuildMovieAndShowIndex() ;
		bmasi.resetCollections() ;
		bmasi.buildMovieIndex() ;
		bmasi.recordMovieAndShowInfo() ;
		bmasi.findAndRecordHDandSDMoviesAndShows() ;
		System.out.println( "Shutdown." ) ;
	}

	/**
	 * Place the tv show or movie file into the given storageMap. This could be either a tv show entry or movie entry.
	 * Helper method for buildMovieIndex().
	 * @param storageMap: Place to store the movie or tv show
	 * @param movieOrTVShowName: The name of the movie or tv show. Will always be non-null
	 * @param tvShowSeasonName: Null if the entry is a movie; a season name if tv.
	 * @param theFile
	 */
	private MovieAndShowInfo addEntryToMap( Map< String, MovieAndShowInfo > storageMap,
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
		// Post condition: mapEntry is non-null and exists in the storageMap.
		// Note that addMP4File/addMKVFile below will build correlations for each file.
		if( isMP4 )
		{
			mapEntry.addMP4File( probeResult ) ;
		}
		else
		{
			mapEntry.addMKVFile( probeResult ) ;
		}
		return mapEntry ;
	}

	/**
	 * The purpose of this method is to build a single Movie or TV Show (MovieAndShowInfo instance)
	 *  for each movie or tv show. The method will populate the movieMap and tvShowMap with the
	 *  resulting information, including to add all mp4 and mkv files to each movie or tv show.
	 * This method only populates the movieMap and tvShowMap, but does NOT write anything
	 *  to the database.
	 */
	private void buildMovieIndex()
	{
		log.info( "Building movie index..." ) ;

		// First, let's pull the info from the probeInfoCollection
		log.info( "Running find..." ) ;
		Bson findFilesFilter = Filters.regex( "fileNameWithPath", ".*" ) ;
		FindIterable< FFmpegProbeResult > probeInfoFindResult = probeInfoCollection.find( findFilesFilter ) ;

		int numMovies = 0 ;
		int numTVShows = 0 ;
		int numOtherVideos = 0 ;
		int numParseErrors = 0 ;

		// Walk through the list of probe records
		Iterator< FFmpegProbeResult > probeInfoFindResultIterator = probeInfoFindResult.iterator() ;
		while( probeInfoFindResultIterator.hasNext() )
		{
			FFmpegProbeResult probeResult = probeInfoFindResultIterator.next() ;
			File theFile = new File( probeResult.getFileNameWithPath() ) ;
			if( theFile.getParent().contains( "TV Shows" ) )
			{
				// TV Show
				++numTVShows ;

				final String tvShowName = theFile.getParentFile().getParentFile().getName() ;
//				final String tvShowSeasonName = theFile.getParentFile().getName() ;
				log.fine( "Found TV show: " + tvShowName + ", filename: " + theFile.getName() ) ;

				MovieAndShowInfo entry = addEntryToMap( tvShowMap, tvShowName, probeResult, theFile ) ;
				entry.setMovieOrShowName( tvShowName ) ;
				entry.setTVShow( true ) ;
//				entry.setTVShowSeasonName( tvShowSeasonName ) ;
			}
			else if( theFile.getParent().contains( "(" ) )
			{
				// Movie
				++numMovies ;

				// The formal filename should be like this:
				// \\yoda\Backup\Movies\Transformers (2007)\Making Of-behindthescenes.mkv
				final String movieName = theFile.getParentFile().getName() ;
				log.fine( "Found movie: " + movieName + ", filename: " + theFile.getName() ) ;
				// movieName should be of the form "Transformers (2007)"
				addEntryToMap( movieMap, movieName, probeResult, theFile ) ;
			}
			else if( theFile.getAbsolutePath().contains( "Other Videos" ) )
			{
				// Do nothing for other videos
				++numOtherVideos ;
			}
			else
			{
				++numParseErrors ;
				log.warning( "Parse error for file: " + theFile.getAbsolutePath() ) ;
			}
		} // while( iterator.hasNext() )
		log.info( "Done building movie index. Found " + numMovies + " movie file(s), " + numTVShows + " tv show file(s), "
				+ numOtherVideos + " other video file(s), and " + numParseErrors + " parse error(s)" ) ;
	}

	/**

	 * Build two lists sd and hd movies and shows and place them
	 * into the database.
	 */
	private void findAndRecordHDandSDMoviesAndShows()
	{
		log.info( "Finding and recording HD and SD Movies..." ) ;
		findAndRecordHDandSDMoviesAndShowsWithInputMap( movieMap ) ;
		log.info( "Done finding and recording HD and SD Movies." ) ;

		log.info( "Finding and recording HD and SD TV Shows..." ) ;
		findAndRecordHDandSDMoviesAndShowsWithInputMap( tvShowMap ) ;
		log.info( "Done finding and recording HD and SD TV Shows." ) ;
	}

	/**
	 * Find and record to the database the HD and SD files from item in the inputMap.
	 * @param inputMap
	 */
	private void findAndRecordHDandSDMoviesAndShowsWithInputMap( Map< String, MovieAndShowInfo > inputMap )
	{
		List< HDorSDFile > hdFiles = new ArrayList< HDorSDFile >() ;
		List< HDorSDFile > sdFiles = new ArrayList< HDorSDFile >() ;
		for( Map.Entry< String, MovieAndShowInfo > set : inputMap.entrySet() )
		{
			String movieOrShowName = set.getKey() ;
			MovieAndShowInfo movieAndShowInfo = set.getValue() ;

			FFmpegProbeResult largestFile = movieAndShowInfo.findLargestFile() ;
			if( null == largestFile )
			{
				log.warning( "Found null largestFile for MovieAndShowInfo: " + movieAndShowInfo.toString() ) ;
				continue ;
			}
			// Post condition: largestFile is the largest file in this movie or show, and is non-null
			List< FFmpegStream > streams = largestFile.getStreams() ;
			if( null == streams )
			{
				log.warning( "Found null streams for MovieAndShowInfo: " + movieAndShowInfo.toString() ) ;
				continue ;
			}

			// Stream[0] is always the video file.
			if( streams.isEmpty() )
			{
				log.warning( "Found empty streams for MovieAndShowInfo: " + movieAndShowInfo.toString() ) ;
				continue ;
			}

			FFmpegStream videoStream = streams.get( 0 ) ;
			if( videoStream.height >= 720 )
			{
				// HD
				hdFiles.add( new HDorSDFile( movieOrShowName ) ) ;
			}
			else
			{
				sdFiles.add( new HDorSDFile( movieOrShowName ) ) ;
			}
		} // for( movieMap )

		// Find done, now record.
		if( !hdFiles.isEmpty() )
		{
			Collections.sort( hdFiles ) ;
			hDMoviesAndShowsCollection.insertMany( hdFiles ) ;
		}
		if( !sdFiles.isEmpty() )
		{
			Collections.sort( sdFiles ) ;
			sDMoviesAndShowsCollection.insertMany( sdFiles ) ;
		}
	}

	/**

	 * Build a correlated files list for each entry in the movieMap and tvShowMap and store them all into
	 *  the database movieAndShowInfoCollection.
	 * @param inputMap

	 */
	private void recordMovieAndShowInfo()
	{
		log.info( "Recording " + movieMap.size() + " movies..." ) ;
		recordMoviesAndShowsWithInputMap( movieMap ) ;
		log.info( "Done recording movies." ) ;

		log.info( "Recording " + tvShowMap.size() + " TV show seasons..." ) ;
		recordMoviesAndShowsWithInputMap( tvShowMap ) ;
		log.info( "Done recording TV Shows." ) ;
	}

	/**
	 * Build a correlated files list for each entry in the inputMap and store them all into
	 *  the database movieAndShowInfoCollection.
	 * @param inputMap
	 */
	private void recordMoviesAndShowsWithInputMap( final Map< String, MovieAndShowInfo > inputMap )
	{
		if( inputMap.isEmpty() )
		{
			return ;
		}

		// Used a Map<> internally for quick search, but the database only accepts (so far as I can
		// figure out) linear Collections.
		List< MovieAndShowInfo > moviesAndShowsInfo = new ArrayList< MovieAndShowInfo >() ;
		for( Map.Entry< String, MovieAndShowInfo > set : inputMap.entrySet() )
		{
			//			String movieOrShowName = set.getKey() ;
			MovieAndShowInfo movieAndShowInfo = set.getValue() ;
			
			// This will build the correlated files inside of the movieAndShowInfo
			// Do this once for each file
			movieAndShowInfo.makeReadyCorrelatedFilesList() ;
			moviesAndShowsInfo.add( movieAndShowInfo ) ;
		}
		Collections.sort( moviesAndShowsInfo ) ;
		movieAndShowInfoCollection.insertMany( moviesAndShowsInfo ) ;
	}

	/**
	 * Remove all objects from each collection, except the ProbeInfo collection, and establish
	 *  a new collection for each.
	 */
	private void resetCollections()
	{
		log.info( "Resetting collections..." ) ;

		masMDB.dropMovieAndShowInfoCollection() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		
		masMDB.dropHDMoviesAndShowCollection() ;
		hDMoviesAndShowsCollection = masMDB.getHDMoviesAndShowsCollection() ;

		masMDB.dropSDMoviesAndShowCollection() ;
		sDMoviesAndShowsCollection = masMDB.getSDMoviesAndShowsCollection() ;

		log.info( "Done resetting collections." ) ;
	}
}
