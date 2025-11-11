package run_ffmpeg.TheMovieDB;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import run_ffmpeg.Common;

public class TheMovieDB
{
	protected final String apikey = "915b0698-6ce6-4a44-871e-0cb6f8dcb62c" ;

	/// The apiToken is used as a session key and will change roughly every 30 days
	/// It is retrieved via login()
	protected String apiToken = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIzNmZiNmFjZjM1NDI3ZGRmNDdkYmJmZmQ4ZGQ4ZGNhZiIsIm5iZiI6MTY1NDQ1NzQxNS42MDQsInN1YiI6IjYyOWQwNDQ3N2Q1ZGI1MDg2YTQ5Yjg4NCIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.ZQageqYhhYBIy7bS0oihUtOmHe5wzOf5If1qXx9rPxM" ;
	protected final String theMovieDBBaseURI = "https://api.themoviedb.org/3" ;
	protected TheMovieDB_configurationResponse configuration = null ;
	protected String posterBaseURI = theMovieDBBaseURI + "/t/p/original" ;

	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_themoviedb.txt" ;


	public TheMovieDB()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		login() ;
		getConfiguration() ;
	}

	public TheMovieDB( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;

		login() ;
		getConfiguration() ;
	}

	public static void main( String[] args )
	{
		(new TheMovieDB()).runTests() ;
	}

	protected void runTests()
	{
		log.info( "Running tests..." ) ;
		final TheMovieDB_findResponse aBeautifulDayInTheNeighborhoodResponse = findByIMDBId( "3224458" ) ;
		log.info( "aBeautifulDayInTheNeighborhoodResponse: " + aBeautifulDayInTheNeighborhoodResponse.toString() ) ;
		
		// Test the poster and logo sizes.
		// Use logo size w154: http://image.tmdb.org/t/p/w154/p9vCAVhDK375XyobVcKqzqzsUHE.jpg
//		List< String > urls = new ArrayList< String >() ;
//		configuration.images.logo_sizes.forEach( size -> {
//			urls.add( configuration.images.base_url
//					+ size
//					+ aBeautifulDayInTheNeighborhoodResponse.movie_results.getFirst().poster_path
//					) ;
//			});
//		configuration.images.backdrop_sizes.forEach( size -> {
//			urls.add( configuration.images.base_url
//					+ size
//					+ aBeautifulDayInTheNeighborhoodResponse.movie_results.getFirst().poster_path
//					) ;
//			});
//		configuration.images.poster_sizes.forEach( size -> {
//			urls.add( configuration.images.base_url
//					+ size
//					+ aBeautifulDayInTheNeighborhoodResponse.movie_results.getFirst().poster_path
//					) ;
//			});
//		log.info( urls.toString() ) ;
	}

	public TheMovieDB_findResponse findByIMDBId( final String imdbID )
	{
		Preconditions.checkArgument( imdbID != null ) ;
		Preconditions.checkArgument( !imdbID.isBlank() ) ;
		
		String updatedImdbID = imdbID ;
		if( !imdbID.startsWith( "tt" ) )
		{
			updatedImdbID = "tt" + imdbID ;
		}
		final String uri = getTheMovieDBBaseURI() + "/find/"
				+ updatedImdbID
				+ "?external_source=imdb_id" ;
		final String responseBody = httpGet( uri ) ;
		if( responseBody.isEmpty() )
		{
			log.warning( "Failed to get response for query: " + uri ) ;
			return null ;
		}
		// Successful response

		Gson queryResponseGson = new Gson() ;
		//		Gson queryResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
		final TheMovieDB_findResponse findResponse = queryResponseGson.fromJson( responseBody, TheMovieDB_findResponse.class ) ;
		log.fine( "findResponse: " + findResponse.toString() ) ;

		return findResponse ;
	}
	
	public TheMovieDB_findResponse findByTMDBId( final String tmdbID )
	{
		Preconditions.checkArgument( tmdbID != null ) ;
		Preconditions.checkArgument( !tmdbID.isBlank() ) ;
		
		final String uri = getTheMovieDBBaseURI() + "/find/"
				+ tmdbID
				+ "?external_source=tmdb_id" ;
		final String responseBody = httpGet( uri ) ;
		if( responseBody.isEmpty() )
		{
			log.warning( "Failed to get response for query: " + uri ) ;
			return null ;
		}
		// Successful response

		Gson queryResponseGson = new Gson() ;
		//		Gson queryResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
		final TheMovieDB_findResponse findResponse = queryResponseGson.fromJson( responseBody, TheMovieDB_findResponse.class ) ;
		log.fine( "findResponse: " + findResponse.toString() ) ;

		return findResponse ;
	}
	
	public String getApiKey()
	{
		return apikey ;
	}

	public String getApiToken()
	{
		return apiToken ;
	}

	protected void getConfiguration()
	{
		try
		{
			final String uri = getTheMovieDBBaseURI() + "/configuration" ;
			final String responseBody = httpGet( uri ) ;
			if( responseBody.isEmpty() )
			{
				log.warning( "Failed to get response for query: " + uri ) ;
			}
			// Successful response

			Gson queryResponseGson = new Gson() ;
			//		Gson queryResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
			final TheMovieDB_configurationResponse configurationResponse = queryResponseGson.fromJson( responseBody, TheMovieDB_configurationResponse.class ) ;
			log.info( "configurationResponse: " + configurationResponse.toString() ) ;
			configuration = configurationResponse ;
			
			// Build the URI for retrieving posters
			setPosterBaseURI( configuration.images.base_url + "w154" ) ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}
	
	public String getPosterBaseURI()
	{
		return posterBaseURI ;
	}

	public String getTheMovieDBBaseURI()
	{
		return theMovieDBBaseURI ;
	}

	/**
	 * Issues the HTTP GET query to the given uri.
	 * @param uri
	 * @return GET response body if successful, or empty string ("") if unsuccessful. 
	 */
	public String httpGet( final String uri )
	{
		String retMe = "" ;
		try
		{
			HttpClient client = HttpClient.newHttpClient() ;
			HttpRequest request = HttpRequest.newBuilder()
					.header( "Accept", "application/json" )
					.header( "Authorization", "Bearer " + getApiToken() )
					.uri( URI.create( uri.replace( " ", "%20" ) ) )
					.GET()
					.build() ;
			log.fine( "Sending HttpRequest: " + request.toString() ) ;
			HttpResponse< String > response = client.send(request, HttpResponse.BodyHandlers.ofString() ) ;
			final int responseCode = response.statusCode() ;
			final String responseBody = response.body() ;
	
			log.fine( "responseCode: " + responseCode + ", responseBody: " + responseBody ) ;
	
			if( 200 == responseCode )
			{
				// Successful query
				retMe = responseBody ;
			}
			else
			{
				// Unsuccessful query
				log.warning( "responseCode " + responseCode + " for query " + uri ) ;
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
		return retMe ;
	}

	protected boolean login()
	{
		try
		{
			HttpClient client = HttpClient.newHttpClient() ;
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create( getTheMovieDBBaseURI() + "/authentication" ))
					.header( "accept", "application/json" )
					.header("Authorization", "Bearer " + getApiToken() )
					.method("GET", HttpRequest.BodyPublishers.noBody())
					.build();
			log.fine( "Sending HttpRequest: " + request.toString() ) ;
			HttpResponse< String > response = client.send( request, HttpResponse.BodyHandlers.ofString() ) ;
	
			final int responseCode = response.statusCode() ;
			final String responseBody = response.body() ;
	
			log.info( "responseCode: " + responseCode + ", responseBody: " + responseBody ) ;
			if( responseCode != 200 )
			{
				// Login unsuccessful
				log.warning( "Login failed. responseCode: " + responseCode ) ;
				return false ;
			}
			// Login successful, extract token
	
			Gson loginResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
			final TheMovieDB_loginResponse loginResponse = loginResponseGson.fromJson( response.body(), TheMovieDB_loginResponse.class ) ;
			log.info( "loginResponse: " + loginResponse.toString() ) ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
			return false ;
		}
		return true ;
	}
	
	public TheMovieDB_searchResponse searchMovieByNameAndYear( final String name, final String year )
	{
		Preconditions.checkArgument( name != null ) ;
		Preconditions.checkArgument( !name.isBlank() ) ;
		Preconditions.checkArgument( year != null ) ;
		Preconditions.checkArgument( !year.isBlank() ) ;
		
		final String uri = getTheMovieDBBaseURI() + "/search/movie?query="
				+ name
				+ "&include_adult=false"
				+ "&language=en-US"
				+ "&primary_release_year=" + year
				+ "&page=1"
				+ "&year=" + year ;
		final String responseBody = httpGet( uri ) ;
		if( responseBody.isEmpty() )
		{
			log.warning( "Failed to get response for query: " + uri ) ;
			return null ;
		}
		// Successful response
	
		Gson queryResponseGson = new Gson() ;
		//		Gson queryResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
		final TheMovieDB_searchResponse searchResponse = queryResponseGson.fromJson( responseBody, TheMovieDB_searchResponse.class ) ;
		log.info( "searchResponse: " + searchResponse.toString() ) ;
	
		return searchResponse ;
	}

	public void setPosterBaseURI( final String posterBaseURI )
	{
		this.posterBaseURI = posterBaseURI ;
	}
}
