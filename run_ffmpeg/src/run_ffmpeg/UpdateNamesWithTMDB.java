package run_ffmpeg;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.mongodb.client.MongoCollection;

import run_ffmpeg.TheMovieDB.TheMovieDB;
import run_ffmpeg.TheMovieDB.TheMovieDB_findResponse;
import run_ffmpeg.TheMovieDB.TheMovieDB_searchResponse;
import run_ffmpeg.TheMovieDB.TheMovieDB_searchResult;

public class UpdateNamesWithTMDB
{
	/// Setup the logging subsystem
	protected Logger log = null ;
	protected Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_update_names_with_tmdb.txt" ;

	protected TheMovieDB theMovieDB = null ;
	protected MoviesAndShowsMongoDB masMDB = null ;
	protected MongoCollection< JobRecord_MakeMovieChoice > makeMovieChoiceCollection = null ;
	protected MongoCollection< JobRecord_MadeMovieChoice > madeMovieChoiceCollection = null ;
	
	public UpdateNamesWithTMDB()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		theMovieDB = new TheMovieDB( log, common ) ;
		masMDB = new MoviesAndShowsMongoDB( log ) ;
		makeMovieChoiceCollection = masMDB.getAction_MakeMovieChoiceCollection() ;
		madeMovieChoiceCollection = masMDB.getAction_MadeMovieChoiceCollection() ;
	}

	public static void main( final String[] args )
	{
		(new UpdateNamesWithTMDB()).execute() ;
	}

	public void execute()
	{
		List< String > topLevelDirectories = new ArrayList< String >() ;
		topLevelDirectories.add( "\\\\skywalker\\Media\\Movies" ) ;
		//topLevelDirectories.add( "\\\\skywalker\\Media\\Test" ) ;

		List< File > directoriesToUpdate = getAllMovieDirectories( topLevelDirectories ) ;
		directoriesToUpdate.forEach( directoryToUpdate -> { updateDirectory( directoryToUpdate ); } ) ;
		
		log.info( "Shutdown complete." ) ;
	}

	public void updateDirectory( final File directoryToUpdate )
	{
		Preconditions.checkArgument( directoryToUpdate != null ) ;
		Preconditions.checkArgument( directoryToUpdate.isDirectory() ) ;

		DirectoryNamePattern dnp = new DirectoryNamePattern( log, directoryToUpdate ) ;
		if( dnp.isMovie() && !dnp.hasTmdbInfo() )
		{
			updateMovieDirectory( directoryToUpdate, dnp ) ;
		}
		else if( dnp.isTVShow() && !dnp.hasTvdbInfo() )
		{
			updateTVDirectory( directoryToUpdate, dnp ) ;
		}
	}

	public void updateMovieDirectory( final File directoryToUpdate, final DirectoryNamePattern dnp )
	{
		Preconditions.checkArgument( directoryToUpdate != null ) ;
		Preconditions.checkArgument( dnp != null ) ;
		Preconditions.checkArgument( directoryToUpdate.isDirectory() ) ;

		if( dnp.hasTmdbInfo() )
		{
			return ;
		}
		// PC: Valid movie directory that is missing tmdb info.

		if( dnp.hasImdbInfo() )
		{
			log.info( "Searching by imdbInfo for movie " + dnp.getShowOrMovieName() + ", imdbInfo: " + dnp.getImdbInfo() ) ;
			try
			{
				// Use the imdb info to find the movie info.
				TheMovieDB_findResponse findResponse = theMovieDB.findByIMDBId( dnp.getImdbInfo() ) ;
				if( null == findResponse )
				{
					log.info( "Unable to find results for movie " + dnp.getShowOrMovieName() + ", imdbInfo: " + dnp.getImdbInfo() ) ;
					return ;
				}
				log.info( "findResponse: " + findResponse.toString() ) ;
				// Got the movie information by searching for the imdb info.
				// Update the movie info with the tmbd info
				dnp.setTmdbInfo( Long.toString( findResponse.movie_results.getFirst().id ) ) ;
				
				// Add to workflow collection
				JobRecord_MadeMovieChoice madeMovieChoice = new JobRecord_MadeMovieChoice( directoryToUpdate.getAbsolutePath(),
						findResponse.movie_results.getFirst().id ) ;
				madeMovieChoiceCollection.insertOne( madeMovieChoice ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Exception searching for movie " + dnp.getShowOrMovieName() + ", imdbInfo: " + dnp.getImdbInfo()
				+ ", exception: " + theException.toString() ) ;
			}
			return ;
		}

		// Else -- no IDs with which to search. Need to do a keyword search.
		TheMovieDB_searchResponse searchResponse = theMovieDB.searchMovieByNameAndYear( dnp.getShowOrMovieName(), dnp.getYear() ) ;
		log.info( "Found " + searchResponse.total_results + " results for " + dnp.getAbsolutePath() ) ;
		log.info( searchResponse.toString() ) ;
		
		// Build the object to be posted to the database for user choice.
		JobRecord_MakeMovieChoice movieChoice = new JobRecord_MakeMovieChoice( directoryToUpdate, dnp.getShowOrMovieName() ) ;
		
		// For each movie in searchResponse, create a URL for the poster and add it to the movieChoice
		for( TheMovieDB_searchResult searchResult : searchResponse.results )
		{
			final String posterUrl = theMovieDB.getPosterBaseURI() + searchResult.poster_path ;
			movieChoice.addPair( new ConcretePair_StringLong(posterUrl, searchResult.id ) ) ;
		}
		makeMovieChoiceCollection.insertOne( movieChoice ) ;
	}
	
	public void updateTVDirectory( final File directoryToUpdate, final DirectoryNamePattern dnp )
	{
		Preconditions.checkArgument( directoryToUpdate != null ) ;
		Preconditions.checkArgument( dnp != null ) ;
		Preconditions.checkArgument( directoryToUpdate.isDirectory() ) ;

		if( dnp.hasTmdbInfo() )
		{
			return ;
		}
		// PC: Valid tv show directory that is missing tmdb info.

		if( dnp.hasTvdbInfo() )
		{
			log.info( "Searching by tvdb for tv show " + dnp.getShowOrMovieName() + ", tvdbInfo: " + dnp.getTvdbInfo() ) ;
			try
			{
				// Use the tvdb info to find the movie info.
				TheMovieDB_findResponse findResponse = theMovieDB.findByIMDBId( dnp.getImdbInfo() ) ;
				if( null == findResponse )
				{
					log.info( "Unable to find results for tv show " + dnp.getShowOrMovieName() + ", tvdbInfo: " + dnp.getTvdbInfo() ) ;
					return ;
				}
				log.info( "findResponse: " + findResponse.toString() ) ;
				
				// Got a tv show response; update the dnp with this information
				dnp.setTvdbInfo( Long.toString( findResponse.tv_results.getFirst().id ) ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Exception searching for tv show " + dnp.getShowOrMovieName() + ", tvdbInfo: " + dnp.getTvdbInfo()
				+ ", exception: " + theException.toString() ) ;
			}
			return ;
		}
	}

	public List< File > getAllMovieDirectories( final List< String > directoryNamesToUpgrade )
	{
		Preconditions.checkArgument( directoryNamesToUpgrade != null ) ;

		List< File > directoriesToUpdate = new ArrayList< File >() ;

		for( String directoryNameToUpgrade : directoryNamesToUpgrade )
		{
			final File topLevelDirectory = new File( directoryNameToUpgrade ) ;
			if( !topLevelDirectory.exists() || !topLevelDirectory.isDirectory() )
			{
				continue ;
			}
			// PC: TLD exists and is a directory
			final File[] subDirectories = topLevelDirectory.listFiles( File::isDirectory ) ;
			directoriesToUpdate.addAll( Arrays.asList( subDirectories ) ) ;
		}
		return directoriesToUpdate ;		
	}
}
