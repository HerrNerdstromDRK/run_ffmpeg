package run_ffmpeg.TheTVDB;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import run_ffmpeg.Common;

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
	protected String apiToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhZ2UiOiIiLCJhcGlrZXkiOiI5MTViMDY5OC02Y2U2LTRhNDQtODcxZS0wY2I2ZjhkY2I2MmMiLCJjb21tdW5pdHlfc3VwcG9ydGVkIjpmYWxzZSwiZXhwIjoxNzQ4Mjc1MjYzLCJnZW5kZXIiOiIiLCJoaXRzX3Blcl9kYXkiOjEwMDAwMDAwMCwiaGl0c19wZXJfbW9udGgiOjEwMDAwMDAwMCwiaWQiOiIyNzE0OTkxIiwiaXNfbW9kIjpmYWxzZSwiaXNfc3lzdGVtX2tleSI6ZmFsc2UsImlzX3RydXN0ZWQiOmZhbHNlLCJwaW4iOiJzdHJpbmciLCJyb2xlcyI6W10sInRlbmFudCI6InR2ZGIiLCJ1dWlkIjoiIn0.QJ-nQ7rOIsyRgAAG4LXD1mJQaXXXw5Y95hkkh3in7ISWq7Du4SuSh4BGOk3ON-6dVYE39H99Uihr0DUNrCEKjgmza_M3XV346gAnQyMrk7iqkrh0fNsgd9R_2oSc0Wrh-6JqgSQHdJZTvxfDIg_fmM25wXQj3m_Upu5UYZH80ljFBUhQEU4kGyhCM7gsORlrBwTDi4BvDGcTWZ7hpKHvHVOqPEHN2JVZhqZjdCVf41lUg4l5HEQ9qfPNENbuBEdOVKXwQYAspNpKEdf6G5DJghJ_2gy6ORHfcQ34Gpyh6P9eYjXZ2Sk5sDSNI2V5bAKbPCuzGaaRvLHn1ouoL_VAdtrWxqMnSFo3VgZc-VBODuHMewy-pmekTutC7eATtZldHW5pmI65hIFfVr3RvpNYBnFLaA2DQHuMRJAABtIJNUqKlbODOI78WLusWePWfFO-a9rPIAWzOQ8V-4-6af_rv-tG5qHTflzPG7b_hRE042uBbV4NMijP0MhoP8_fivr0F8h9NRS-gi61H1yOclnf1GQgmxlN0_OCgM03PxRylCQt94bqQcqJP5bXTqy_Jlohybzqw3Xc5Q-2Fdhflom-MvMA5s3zZH0VOwcA5HgQUs7Vhoxgret6prjb_d47s6y_h_U-M4VyM6zH7jEQoXIYVH53po_TKVEqyTuK2XuJPdM" ;
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

	public TheTVDB( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;
		
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
		for( TheTVDB_episodeClass episode : seriesEpisodesInfo.data.episodes )
		{
			log.fine( seriesEpisodesInfo.data.series.name
					+ "(" + seriesEpisodesInfo.data.series.id
					+ ") S" + episode.seasonNumber
					+ "E" + episode.number
					+ ": " + episode.name ) ;
		}

//		TheTVDB_ShowInfo showInfo_TheExpanse = getFullShowInfo( Integer.valueOf( 280619 ) ) ;
//		log.info( showInfo_TheExpanse.toString() ) ;
	}

	public String getApiKey()
	{
		return apikey ;
	}

	public String getPin()
	{
		return pin ;
	}

	public TheTVDB_ShowInfo getFullShowInfo( final Integer showId )
	{
		assert( showId != null ) ;
		assert( showId.intValue() > 0 ) ;

		TheTVDB_ShowInfo showInfo = new TheTVDB_ShowInfo( showId ) ;

		// Request information about each season until we receive an empty episodes list for that season.
		for( int seasonNumber = 1 ; seasonNumber <= 100 ; ++seasonNumber )
		{
			TheTVDB_seriesEpisodesClass seriesEpisodesInfo = getSeriesEpisodesInfo( showId.toString(), "" + seasonNumber ) ;
			if( (null == seriesEpisodesInfo)
					|| (null == seriesEpisodesInfo.data.episodes)
					|| seriesEpisodesInfo.data.episodes.isEmpty() )
			{
				log.fine( "Found empty episodes for show " + showId + " (" + showId.toString() + "), seasonNumber: " + seasonNumber ) ;
				break ;
			}
			// Post condition: Found object for this show and season number

			// Check if this season exists
			if( null == seriesEpisodesInfo.data.episodes )
			{
				// No such season; assume we have reached the end of the seasons for this show
				break ;
			}
			// PC: Got a valid season (hopefully)

			// Find the number of episodes in this season
			final int numEpisodes = seriesEpisodesInfo.data.episodes.size() ;
			log.fine( "Found " + numEpisodes + " episode(s) for " + showId.toString() + " season #" + seasonNumber ) ;

			TheTVDB_seasonInfo seasonInfo = new TheTVDB_seasonInfo( seasonNumber, numEpisodes ) ;

			// Walk through the list of episodes and capture each
			for( TheTVDB_episodeClass episode : seriesEpisodesInfo.data.episodes )
			{
				//				log.fine( seriesEpisodesInfo.data.series.name
				//						+ "(" + seriesEpisodesInfo.data.series.id
				//						+ ") S" + episode.seasonNumber
				//						+ "E" + episode.number
				//						+ ": " + episode.name ) ;

				final int episodeNumber = episode.number ;
				final String episodeName = episode.name ;

				seasonInfo.addEpisode( episodeNumber, episodeName ) ;
			}
			log.fine( "Added " + seasonInfo.getNumEpisodes() + " episode(s) for show " + showId.toString() + " season " + seasonNumber ) ;

			showInfo.addSeason( seasonNumber, seasonInfo ) ;
		}

		return showInfo ;
	}

	public TheTVDB_seriesEpisodesClass getSeriesEpisodesInfo( final String seriesID, final String seasonNumber )
	{
		assert( seriesID != null ) ;
		assert( !seriesID.isBlank() ) ;

		assert( seasonNumber != null ) ;
		assert( !seasonNumber.isBlank() ) ;

		final String uri = theTVDBBaseURI + "/series/"
				+ seriesID
				+ "/episodes/official?"
				+ "page=0&"
				+ "season=" + seasonNumber ;
		final String responseBody = httpGet( uri ) ;
		if( responseBody.isEmpty() )
		{
			log.warning( "Failed to get response for query: " + uri ) ;
			return null ;
		}
		// Successful response

		Gson queryResponseGson = new Gson() ;
		//		Gson queryResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
		final TheTVDB_seriesEpisodesClass seriesEpisodesInfo = queryResponseGson.fromJson( responseBody, TheTVDB_seriesEpisodesClass.class ) ;
		log.fine( "seriesEpisodesInfo: " + seriesEpisodesInfo.toString() ) ;

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

		Gson queryResponseGson = new Gson() ;
		//		Gson queryResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
		final TheTVDB_seriesClass seriesInfo = queryResponseGson.fromJson( responseBody, TheTVDB_seriesClass.class ) ;
		log.fine( "seriesInfo: " + queryResponseGson.toJson( seriesInfo ).toString() ) ;

		return seriesInfo ;
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

			log.fine( "responseCode: " + responseCode + ", responseBody: " + responseBody ) ;
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
			log.fine( "sessionToken: " + sessionToken ) ;
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
}
