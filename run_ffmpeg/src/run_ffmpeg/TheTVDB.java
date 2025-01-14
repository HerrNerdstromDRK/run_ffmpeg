package run_ffmpeg;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * This class encapsulates the interface to TheTVDB.
 */
public class TheTVDB
{
	//// THETVDB API key
	protected final String apikey = "915b0698-6ce6-4a44-871e-0cb6f8dcb62c" ;
	protected final String pin = "string" ;

	/// The apiToken is used as a session key and will change roughly every 30 days
	/// It is retrieved via login()
	protected String apiToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhZ2UiOiIiLCJhcGlrZXkiOiI5MTViMDY5OC02Y2U2LTRhNDQtODcxZS0wY2I2ZjhkY2I2MmMiLCJjb21tdW5pdHlfc3VwcG9ydGVkIjpmYWxzZSwiZXhwIjoxNzM5MDk3NjI0LCJnZW5kZXIiOiIiLCJoaXRzX3Blcl9kYXkiOjEwMDAwMDAwMCwiaGl0c19wZXJfbW9udGgiOjEwMDAwMDAwMCwiaWQiOiIyNzE0OTkxIiwiaXNfbW9kIjpmYWxzZSwiaXNfc3lzdGVtX2tleSI6ZmFsc2UsImlzX3RydXN0ZWQiOmZhbHNlLCJwaW4iOiJzdHJpbmciLCJyb2xlcyI6W10sInRlbmFudCI6InR2ZGIiLCJ1dWlkIjoiIn0.SBaIRzoJCDxaZHwqcObGKq_dw_FxZacSeiEk8jJz6JsqmURSnsHz4owxklS8ZuwcaElwpI1UH8daxf5n56Vv3NkepgLtWqltlGc-IrvnvqsihnEf2qrDI9Zw1m0xS2_QLyDsvVLAc9ZfDG2o8r4x0P0I00iWCsbuxJPJz_bSHDrv7nnX4inGFlWPQtS1O6BNh66WozP_Ny3PAL4EvYjIOz89_OSJD8uNIBYLa3CYgvFieq4jiFK87hyrb_fd8qN7PB7idYUx63Gwl6mY4vKv--pElF7ekPB_BB-K-J935uRH1qqAnvdMnTX31cwWaPQUnX_LudLMwUv1K25qnFReNjHBVnoslQEr1khEK4tVxISMAsZaC_JatoaRiSPBJQc3KUOK-ZC16tRZtML5qVnVhj5AtWbfF2UX6GPFxJRFwHMES7i7C1Pxl40H2LxRmYXYpYELgkbXhxn0JDYvUKutkF_ezjp0QLyLzieyw_geASD2cn5feTI7gmBFnUAAVHEyLVMRb4NzYDWUJUnT57y8rd7lnGmOfJ6reDdI_8n_rRK708_eIvWZYQMV3j5RTjiXPnF01lCmb5CWNiRWnTnrynE9zxoyhsZHpIddewGXKUuuWgFk1l3mwcJx_-5AyqP3TLE55_OxbV9sUmdksRvi6V_8IkLkZhCB5Empmcq2D2w" ;
	protected final String theTVDBBaseURI = "https://api4.thetvdb.com/v4" ;

	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_thetvdb.txt" ;

	public TheTVDB()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		
		login() ;
	}
	
	public static void main( String[] args )
	{
		(new TheTVDB()).runTests() ;
	}
	
	protected void runTests()
	{
		TheTVDB_searchResponse searchResponse = queryTVSeriesByName( "The Expanse" ) ;
		assert( searchResponse != null ) ;
		
		// The Expanse series id is 280619
		TheTVDB_seriesClass seriesInfo = getSeriesInfo( "280619" ) ;
		assert( seriesInfo != null ) ;
		
		TheTVDB_seriesEpisodesClass seriesEpisodesInfo = getSeriesEpisodesInfo( "280619", "1" ) ;
		assert( seriesEpisodesInfo != null ) ;
	}

	public TheTVDB_seriesEpisodesClass getSeriesEpisodesInfo( final String seriesID, final String seasonNumber )
	{
		assert( seriesID != null ) ;
		assert( !seriesID.isBlank() ) ;
		
		assert( seasonNumber != null ) ;
		assert( !seasonNumber.isBlank() ) ;
		
		final String uri = theTVDBBaseURI + "/series/"
				+ seriesID
				+ "/episodes/default?"
				+ "page=0&"
				+ "season=" + seasonNumber ;
		final String responseBody = httpGet( uri ) ;
		if( responseBody.isEmpty() )
		{
			log.warning( "Failed to get response for query: " + uri ) ;
			return null ;
		}
		// Successful response
		
		Gson queryResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
		final TheTVDB_seriesEpisodesClass seriesEpisodesInfo = queryResponseGson.fromJson( responseBody, TheTVDB_seriesEpisodesClass.class ) ;
		log.fine( "seriesEpisodesInfo: " + queryResponseGson.toJson( seriesEpisodesInfo ).toString() ) ;
		
		return seriesEpisodesInfo ;
	}
	
	public TheTVDB_seriesClass getSeriesInfo( final String seriesID )
	{
		assert( seriesID != null ) ;
		assert( !seriesID.isBlank() ) ;

		
		final String uri = theTVDBBaseURI + "/series/"
				+ seriesID ;
//				+ URLEncoder.encode( seriesID, StandardCharsets.UTF_8 ) ;
		final String responseBody = httpGet( uri ) ;
		if( responseBody.isEmpty() )
		{
			log.warning( "Failed to get response for query: " + uri ) ;
			return null ;
		}
		// Successful response
		
		Gson queryResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
		final TheTVDB_seriesClass seriesInfo = queryResponseGson.fromJson( responseBody, TheTVDB_seriesClass.class ) ;
		log.fine( "seriesInfo: " + queryResponseGson.toJson( seriesInfo ).toString() ) ;
		
		return seriesInfo ;
	}
	
	/**
	 * Query a tv series by series name.
	 * @param seriesName
	 * @return A TheTVDB_searchResponse object. Returns null if an error occurs.
	 */
	public TheTVDB_searchResponse queryTVSeriesByName( final String seriesName )
	{
		assert( seriesName != null ) ;
		assert( !seriesName.isBlank() ) ;
		
		final String uri = theTVDBBaseURI + "/search?"
				+ "query="
				+ URLEncoder.encode( seriesName, StandardCharsets.UTF_8 )
				+ "&type=series" ;
		final String responseBody = httpGet( uri ) ;
		if( responseBody.isEmpty() )
		{
			log.warning( "Failed to get response for query: " + uri ) ;
			return null ;
		}
		// Successful response
		
		Gson queryResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
		final TheTVDB_searchResponse searchResponse = queryResponseGson.fromJson( responseBody, TheTVDB_searchResponse.class ) ;
//		log.info( "searchResponse: " + queryResponseGson.toJson( searchResponse ).toString() ) ;

		return searchResponse ;
	}
	
	protected boolean login()
	{
		final TheTVDB_loginRequest loginRequest = new TheTVDB_loginRequest( getApiKey(), getPin() ) ;
		
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( loginRequest ) ;
//		log.info( "loginRequestJson: " + loginRequestJson ) ;

		try
		{
			HttpClient client = HttpClient.newHttpClient() ;
			HttpRequest request = HttpRequest.newBuilder()
					.header( "Accept", "application/json" )
					.header( "Content-Type", "application/json" )
					.uri( URI.create( theTVDBBaseURI + "/login" ) )
					.POST( HttpRequest.BodyPublishers.ofString( loginRequestJson ) )
					.build() ;
			log.fine( "Sending HttpRequest: " + request.toString() ) ;
			HttpResponse< String > response = client.send(request, HttpResponse.BodyHandlers.ofString() ) ;

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
			final TheTVDB_loginResponse loginResponse = loginResponseGson.fromJson( response.body(), TheTVDB_loginResponse.class ) ;
			log.fine( "loginResponse: " + loginResponseGson.toJson( loginResponse ).toString() ) ;
			final String sessionToken = loginResponse.data.get( "token" ) ;
			log.info( "sessionToken: " + sessionToken ) ;
			if( sessionToken != null )
			{
				apiToken = sessionToken ;
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
			return false ;
		}
		return true ;
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
					.header( "Authorization", "Bearer " + apiToken )
					.uri( URI.create( uri ) )
					.GET()
					.build() ;
			log.info( "Sending HttpRequest: " + request.toString() ) ;
			HttpResponse< String > response = client.send(request, HttpResponse.BodyHandlers.ofString() ) ;
			final int responseCode = response.statusCode() ;
			final String responseBody = response.body() ;
			
			log.info( "responseCode: " + responseCode + ", responseBody: " + responseBody ) ;
			
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
	
	public String getApiKey()
	{
		return apikey ;
	}
	
	public String getPin()
	{
		return pin ;
	}	
}
