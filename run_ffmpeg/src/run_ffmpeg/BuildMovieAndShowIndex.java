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

	/// File name to which to log activities for this application.
	private static final String logFileName = "build_movie_and_show_index.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_build_movie_and_show_index.txt" ;
	
	private MoviesAndShowsMongoDB masMDB = null ;
	private MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;
	private MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;
	private MongoCollection< MissingFile > missingFileCollection = null ;
	private MongoCollection< HDorSDFile > hDMoviesAndShowsCollection = null ;
	private MongoCollection< HDorSDFile > sDMoviesAndShowsCollection = null ;
	
	private Map< String, MovieAndShowInfo > movieMap = new HashMap< String, MovieAndShowInfo >() ;
	private Map< String, MovieAndShowInfo > tvShowMap = new HashMap< String, MovieAndShowInfo >() ;
	
	public BuildMovieAndShowIndex()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		
		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB() ;
		
		// Retrieve the db collections.
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		missingFileCollection = masMDB.getMissingFileCollection() ;
		hDMoviesAndShowsCollection = masMDB.getHDMoviesAndShowsCollection() ;
		sDMoviesAndShowsCollection = masMDB.getSDMoviesAndShowsCollection() ;
	}
	
	public static void main( String[] args )
	{
		BuildMovieAndShowIndex bmasi = new BuildMovieAndShowIndex() ;
		bmasi.resetCollections() ;
		bmasi.buildMovieIndex() ;
		bmasi.recordMovieAndShowInfo() ;
//		bmasi.findAndRecordMissingFiles() ;
		bmasi.findAndRecordHDandSDMoviesAndShows() ;
	}

	/**
	 * Remove all objects from each collection, except the ProbeInfo collection, and establish
	 *  a new collection for each.
	 */
	private void resetCollections()
	{
		log.info( "Resetting collections..." ) ;
		masMDB.dropMissingFileCollection() ;
		missingFileCollection = masMDB.getMissingFileCollection() ;

		masMDB.dropMovieAndShowInfoCollection() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		
		masMDB.dropHDMoviesAndShowCollection() ;
		hDMoviesAndShowsCollection = masMDB.getHDMoviesAndShowsCollection() ;

		masMDB.dropSDMoviesAndShowCollection() ;
		sDMoviesAndShowsCollection = masMDB.getSDMoviesAndShowsCollection() ;
		
		masMDB.dropMovieAndShowInfoCollection() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		
		log.info( "Done resetting collections." ) ;
	}
	
	private void recordMovieAndShowInfo()
	{
		log.info( "Recording movies..." ) ;
		recordMoviesAndShowsWithInputMap( movieMap ) ;
		log.info( "Done recording movies." ) ;

		log.info( "Recording TV shows..." ) ;
		recordMoviesAndShowsWithInputMap( tvShowMap ) ;
		log.info( "Done recordingTV Shows." ) ;
	}
	
	private void recordMoviesAndShowsWithInputMap( final Map< String, MovieAndShowInfo > inputMap )
	{
		if( inputMap.isEmpty() )
		{
			return ;
		}
		
		List< MovieAndShowInfo > moviesAndShowsInfo = new ArrayList< MovieAndShowInfo >() ;
		for( Map.Entry< String, MovieAndShowInfo > set : inputMap.entrySet() )
		{
//			String movieOrShowName = set.getKey() ;
			MovieAndShowInfo movieAndShowInfo = set.getValue() ;
			movieAndShowInfo.makeReadyCorrelatedFilesList() ;
			moviesAndShowsInfo.add( movieAndShowInfo ) ;
		}
		Collections.sort( moviesAndShowsInfo ) ;
		movieAndShowInfoCollection.insertMany( moviesAndShowsInfo ) ;
	}
	
	/**
	 * Front end method to get around the static call from main().
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
	 * Find and record to the database the HD and SD files from each movie or show.
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
	 * The purpose of this method is to build a single Movie or TV Show (MovieAndShowInfo instance)
	 * for each movie or tv show. The method will populate the movieMap and tvShowMap with the
	 * resulting information, including to add all mp4 and mkv fiels to each movie or tv show.
	 * TODO: Write the entries to the database?
	 */
	private void buildMovieIndex()
	{
		log.info( "Building movie index..." ) ;
		
		// First, let's pull the mkv file info
		Bson mp4A = Filters.regex( "filename", ".*Pearl Harbor.*" ) ;
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
				log.fine( "Found TV show: " + tvShowName + ", filename: " + theFile.getName() ) ;
				
				addEntryToMap( tvShowMap, tvShowName, probeResult, theFile ) ;
			}
			else if( theFile.getParent().contains( "(" ) )
			{
				// Movie
				// The formal should be like this:
				// \\yoda\Backup\Movies\Transformers (2007)\Making Of-behindthescenes.mkv
				final String movieName = theFile.getParentFile().getName() ;
				log.fine( "Found movie: " + movieName + ", filename: " + theFile.getName() ) ;
				// movieName should be of the form "Transformers (2007)"
				addEntryToMap( movieMap, movieName, probeResult, theFile ) ;
			}
			else
			{
				log.warning( "Parse error for file: " + theFile.getAbsolutePath() ) ;
			}
		} // while( iterator.hasNext() )
		log.info( "Done building movie index." ) ;
	}
	
	/**
	 * This method will look through the movieMap and tvShowMap and look for missing mkv or mp4 files.
	 * It will write any missing files to the database.
	 */
	private void findAndRecordMissingFiles()
	{
		log.info( "Retrieving all missing files..." ) ;
		// Done reading the mkv and mp4 files from the database.
		// Sort through each movie and tv show to find matching filenames.
		List< MissingFile > missingFiles = correlateFiles( movieMap ) ;
		missingFiles.addAll( correlateFiles( tvShowMap ) ) ;

		// Now have all of the missing files.
		// Store them in the collection
		log.info( "Writing missing files to database" ) ;
		missingFileCollection.insertMany( missingFiles ) ;
		
		log.info( "Done retrieving missing files." ) ;
	}
	
	/**
	 * Helper method for findAndRecordMissingFiles().
	 * @param probeMap
	 * @return
	 */
	private List< MissingFile > correlateFiles( Map< String, MovieAndShowInfo > probeMap )
	{
		List< MissingFile > missingFiles = new ArrayList< MissingFile >() ;
		// Iterate through all items in the probeMap and invoke each correlation method
		// Note that the items in the probeMap should be unique
		for( Map.Entry< String, MovieAndShowInfo > set : probeMap.entrySet() )
		{
//			String movieOrShowName = set.getKey() ;
			MovieAndShowInfo movieAndShowInfo = set.getValue() ;
//			missingFiles.addAll( movieAndShowInfo.reportMissingFiles() ) ;
		}
		return missingFiles ;
	}
	
	/**
	 * Place the tv show or movie file into the given storageMap. This could be either a tv show entry or movie entry.
	 * Helper method for buildMovieIndex().
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
		// Note that addMP4File/addMKVFile below will build correlations for each file.
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
