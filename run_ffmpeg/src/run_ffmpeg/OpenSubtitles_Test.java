package run_ffmpeg;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import run_ffmpeg.OpenSubtitles.OpenSubtitles_Data;
import run_ffmpeg.OpenSubtitles.OpenSubtitles_LoginRequest;
import run_ffmpeg.OpenSubtitles.OpenSubtitles_LoginResponse;
import run_ffmpeg.OpenSubtitles.OpenSubtitles_SubtitlesResponse;

public class OpenSubtitles_Test
{
	protected final String apikey = "srEjGDGfSbihQctDIlGPjPWEJlHyhpXb" ;
	protected final String username = "HerrNerdstrom" ;
	protected final String password = "d@t2%y'%'fWU$ju" ;
	protected final String userAgent = "run_ffmpeg v0.1" ;

	/// The apiToken is used as a session key and will change roughly every 24 hours
	/// It is retrieved via login()
	protected String apiToken = "" ;

	// baseURI is not final since it could be reassigned by the server
	protected String baseURI = "https://api.opensubtitles.com/api/v1" ;

	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_opensubtitles_test.txt" ;

	public OpenSubtitles_Test()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		login() ;
	}

	public OpenSubtitles_Test( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;

		login() ;
	}

	public static void main( String[] args )
	{
		(new OpenSubtitles_Test()).runTests() ;
	}

	protected void runTests()
	{
		final String imdbID = "1276201" ;
		final OpenSubtitles_SubtitlesResponse subtitlesResponse = searchForSubtitlesByIMDBID( imdbID ) ;
		if( null == subtitlesResponse )
		{
			log.warning( "null subtitlesResponse searching for imdb id " + imdbID ) ;
		}
		if( subtitlesResponse.getData().isEmpty() )
		{
			log.warning( "Found empty data for imdb id " + imdbID ) ;
			return ;
		}
		// PC: subtitlesResponse is non-null and non-empty
		
		// Find the subtitles with the highest download count
		// Note that OpenSubtitles_Attributes.downloadCount can be null
		int highestDLCount = -1 ;
		OpenSubtitles_Data highestDLCountData = subtitlesResponse.getData().getFirst() ;

		if( highestDLCountData.getAttributes().getDownload_count() != null )
		{
			highestDLCount = highestDLCountData.getAttributes().getDownload_count() ;
		}
		for( OpenSubtitles_Data testData : subtitlesResponse.getData() )
		{
			if( (testData.getAttributes().getDownload_count() != null) && (testData.getAttributes().getDownload_count() > highestDLCount) )
			{
				highestDLCountData = testData ;
				highestDLCount = highestDLCountData.getAttributes().getDownload_count() ;
			}
		}
		log.info( "Found highest dl count data: " + highestDLCountData.toString() ) ;
	}

	public OpenSubtitles_SubtitlesResponse searchForSubtitlesByIMDBID( final String idString )
	{
		assert( idString != null ) ;
		assert( !idString.isBlank() ) ;

		final String uri = getBaseURI()
				+ "/subtitles?imdb_id="
				+ idString
				+ "&languages=en" ;
		final String responseBody = httpGet( uri ) ;
		if( responseBody.isEmpty() )
		{
			log.warning( "Failed to get response for query: " + uri ) ;
			return null ;
		}
		// Successful response

		Gson queryResponseGson = new Gson() ;
		//		Gson queryResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
		final OpenSubtitles_SubtitlesResponse subtitlesSearchResponse = queryResponseGson.fromJson( responseBody, OpenSubtitles_SubtitlesResponse.class ) ;
		log.fine( "subtitlesSearchResponse: " + subtitlesSearchResponse.toString() ) ;

		return subtitlesSearchResponse ;
	}

	public String getApiKey()
	{
		return apikey ;
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
					.header( "Authorization", "Bearer " + getAPIToken() )
					.header( "Api-Key", getApiKey() )
					.header( "User-Agent",  getUserAgent() )
					.uri( URI.create( uri ) )
					.GET()
					.build() ;
			log.info( "Sending HttpRequest: " + request.toString() ) ;
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
		final OpenSubtitles_LoginRequest loginRequest = new OpenSubtitles_LoginRequest( getUsername(), getPassword() ) ;

		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( loginRequest ) ;
		log.info( "loginRequestJson: " + loginRequestJson ) ;

		try
		{
			HttpClient client = HttpClient.newHttpClient() ;
			HttpRequest request = HttpRequest.newBuilder()
					.header( "Accept", "application/json" )
					.header( "Content-Type", "application/json" )
					.header( "Api-Key",  getApiKey() )
					.header( "User-Agent",  getUserAgent() )
					.uri( URI.create( getBaseURI() + "/login" ) )
					.POST( HttpRequest.BodyPublishers.ofString( loginRequestJson ) )
					.build() ;
			log.info( "Sending HttpRequest: " + request.toString() ) ;
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
			final OpenSubtitles_LoginResponse loginResponse = loginResponseGson.fromJson( response.body(), OpenSubtitles_LoginResponse.class ) ;
			log.fine( "loginResponse: " + loginResponseGson.toJson( loginResponse ).toString() ) ;

			final String sessionToken = loginResponse.token ;
			log.fine( "sessionToken: " + sessionToken ) ;
			if( sessionToken != null )
			{
				setAPIToken( sessionToken ) ;
			}

			final String newBaseURI = loginResponse.base_url ;
			log.fine( "newBaseURI: " + newBaseURI ) ;
			if( (newBaseURI != null) && !newBaseURI.isBlank() )
			{
				//				setBaseURI( newBaseURI + "/api/v1" ) ;
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
			return false ;
		}
		return true ;
	}

	public String getBaseURI()
	{
		return baseURI ;
	}

	public void setBaseURI( final String baseURI )
	{
		assert( baseURI != null ) ;
		this.baseURI = baseURI ;
	}

	public String getUsername()
	{
		return username ;
	}

	public String getPassword()
	{
		return password ;
	}

	public String getAPIToken()
	{
		return apiToken ;
	}

	public void setAPIToken( final String newToken )
	{
		assert( newToken != null ) ;
		this.apiToken = newToken ;
	}

	public String getUserAgent()
	{
		return userAgent ;
	}
}
