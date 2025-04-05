package run_ffmpeg;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableMap;
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

	/// The list of TVDB show id's for each of the tv shows I am tracking.
	private static final Map< String, Integer > tvShowIDs = ImmutableMap.<String, Integer >builder()
			.put( "30 Rock (2006)", Integer.valueOf( 79488 ) )
			.put( "A Pup Named Scooby-Doo (1988)", Integer.valueOf( 73546 ) )
			.put( "Archer (2009)", Integer.valueOf( 110381 ) )
			.put( "Arrested Development (2003)", Integer.valueOf( 72173 ) )
			.put( "Ahsoka (2024)", Integer.valueOf( 393187 ) )
			.put( "Attack On Pearl Harbor A Day Of Infamy (2007)", Integer.valueOf( 377374 ) )
			.put( "Avatar The Last Airbender (2024)", Integer.valueOf( 385925 ) )
			.put( "Marvels Avengers Assemble (2013)", Integer.valueOf( 264030 ) )
			.put( "Band Of Brothers (2001)", Integer.valueOf( 74205 ) )
			.put( "Banshee (2013)", Integer.valueOf( 259765 ) )
			.put( "Battlestar Galactica (2003)", Integer.valueOf( 73545 ) )
			.put( "Be Cool Scooby-Doo (2015)", Integer.valueOf( 301376 ) )
			.put( "Boardwalk Empire (2010)", Integer.valueOf( 84947 ) )
			.put( "Breaking Bad (2008)", Integer.valueOf( 81189 ) )
			.put( "Brooklyn Nine-Nine (2013)", Integer.valueOf( 269586 ) )
			.put( "Brotherhood (2006)", Integer.valueOf( 79356 ) )
			.put( "Californication (2007)", Integer.valueOf( 80349 ) )
			.put( "Chappelles Show (2003)", Integer.valueOf( 71862 ) )
			.put( "CSI Miami (2002)", Integer.valueOf( 78310 ) )
			.put( "Dark Angel (2000)", Integer.valueOf( 76148 ) )
			.put( "Dexter (2006)", Integer.valueOf( 79349 ) )
			.put( "Doctor Who (2005)", Integer.valueOf( 78804 ) )
			.put( "Entourage (2004)", Integer.valueOf( 74543 ) )
			.put( "ER (1994)", Integer.valueOf( 70761 ) )
			.put( "Family Guy (1999)", Integer.valueOf( 75978 ) )
			.put( "Firefly (2002)", Integer.valueOf( 78874 ) )
			.put( "Foundation (2021)", Integer.valueOf( 366972 ) )
			.put( "Friday Night Lights (2006)", Integer.valueOf( 79337 ) )
			.put( "Game Of Thrones (2011)", Integer.valueOf( 121361 ) )
			.put( "Glee (2009)", Integer.valueOf( 83610 ) )
			.put( "Greys Anatomy (2005)", Integer.valueOf( 73762 ) )
			.put( "Gunpowder (2017)", Integer.valueOf( 334069 ) )
			.put( "Heroes (2006)", Integer.valueOf( 79501 ) )
			.put( "Homeland (2011)", Integer.valueOf( 247897 ) )
			.put( "House (2004)", Integer.valueOf( 73255 ) )
			.put( "House Of The Dragon (2022)", Integer.valueOf( 371572 ) )
			.put( "In Living Color (1990)", Integer.valueOf( 78441 ) )
			.put( "Its Always Sunny In Philadelphia (2005)", Integer.valueOf( 75805 ) )
			.put( "Justified (2010)", Integer.valueOf( 134241 ) )
			.put( "Life (2009)", Integer.valueOf( 117421 ) )
			.put( "MacGyver Classic (1985)", Integer.valueOf( 77847 ) )
			.put( "Magnum P.I. (1980)", Integer.valueOf( 74380 ) )
			.put( "MASH (1972)", Integer.valueOf( 70994 ) )
			.put( "Mickey Mouse Clubhouse (2006)", Integer.valueOf( 79854 ) )
			.put( "NCIS (2003)", Integer.valueOf( 72108 ) )
			.put( "Obi-Wan Kenobi (2022)", Integer.valueOf( 393199 ) )
			.put( "Orange Is The New Black (2013)", Integer.valueOf( 264586 ) )
			.put( "P90X (2004)", Integer.valueOf( 396098 ) )
			.put( "P90X3 (2013)", Integer.valueOf( 394398 ) )
			.put( "Parks And Recreation (2009)", Integer.valueOf( 84912 ) )
			.put( "Paw Patrol (2013)", Integer.valueOf( 272472 ) )
			.put( "Penn And Teller Bullshit (2003)", Integer.valueOf( 72301 ) )
			.put( "Planet Earth (2006)", Integer.valueOf( 79257 ) )
			.put( "Pride And Prejudice (1995)", Integer.valueOf( 71691 ) )
			.put( "Reno 911 (2003)", Integer.valueOf( 72336 ) )
			.put( "Rick And Morty (2013)", Integer.valueOf( 275274 ) )
			.put( "Rizzoli And Iles (2010)", Integer.valueOf( 161461 ) )
			.put( "Rome (2005)", Integer.valueOf( 73508 ) )
			.put( "Schitts Creek (2015)", Integer.valueOf( 287247 ) )
			.put( "Scooby-Doo And Scrappy-Doo (1979)", Integer.valueOf( 78740 ) )
			.put( "Scooby-Doo Where Are You (1969)", Integer.valueOf( 78260 ) )
			.put( "Scrubs (2001)", Integer.valueOf( 76156 ) )
			.put( "Sex And The City (1998)", Integer.valueOf( 76648 ) )
			.put( "Sherlock (2010)", Integer.valueOf( 176941 ) )
			.put( "South Park (1997)", Integer.valueOf( 75897 ) )
			.put( "Star Trek (1966)", Integer.valueOf( 77526 ) )
			.put( "Star Trek Deep Space Nine (1993)", Integer.valueOf( 72073 ) )
			.put( "Star Trek Discovery (2017)", Integer.valueOf( 328711 ) )
			.put( "Star Trek Enterprise (2001)", Integer.valueOf( 73893 ) )
			.put( "Star Trek Picard (2020)", Integer.valueOf( 364093 ))
			.put( "Star Trek Strange New Worlds (2022)", Integer.valueOf( 382389 ) )
			.put( "Star Trek The Next Generation (1987)", Integer.valueOf( 71470 ) )
			.put( "Star Trek Voyager (1995)", Integer.valueOf( 74550 ))
			.put( "Star Wars Rebels (2014)", Integer.valueOf( 283468 ) )
			.put( "Star Wars The Clone Wars (2008)", Integer.valueOf( 83268 ) )
			.put( "Star Wars Skeleton Crew (2024)", Integer.valueOf( 420600 ) )
			.put( "Star Wars Tales Of The Empire (2024)", Integer.valueOf( 448549 ) )
			.put( "Star Wars Tales Of The Jedi (2022)", Integer.valueOf( 420659 ) )
			.put( "Stargate Atlantis (2004)", Integer.valueOf( 70851 ) )
			.put( "Stargate SG-1 (1997)", Integer.valueOf( 72449 ) )
			.put( "Stargate Origins (2018)", Integer.valueOf( 339552 ))
			.put( "Stargate Universe (2009)", Integer.valueOf( 83237 ))
			.put( "Stranger Things (2016)", Integer.valueOf( 305288 ) )
			.put( "Ted Lasso (2020)", Integer.valueOf( 383203 ) )
			.put( "The A-Team (1983)", Integer.valueOf( 77904 ) )
			.put( "The Big Bang Theory (2007)", Integer.valueOf( 80379 ) )
			.put( "The Book Of Boba Fett (2021)", Integer.valueOf( 393589 ) )
			.put( "The Expanse (2015)", Integer.valueOf( 280619 ) )
			.put( "The Good Place (2016)", Integer.valueOf( 311711 ) )
			.put( "The Last Of Us (2023)", Integer.valueOf( 392256 ) )
			.put( "The Legend Of Korra (2012)", Integer.valueOf( 251085 ) )
			.put( "The Lord Of The Rings The Rings Of Power (2022)", Integer.valueOf( 367506 ) )
			.put( "The Mandalorian (2019)", Integer.valueOf( 361753 ) )
			.put( "The Office (2005)", Integer.valueOf( 73244 ) )
			.put( "The Orville (2017)", Integer.valueOf( 328487 ))
			.put( "The Scooby-Doo Show (1976)", Integer.valueOf( 73817 ) )
			.put( "The Simpsons (1989)", Integer.valueOf( 71663 ) )
			.put( "The Sopranos (1999)", Integer.valueOf( 75299 ) )
			.put( "The Tudors (2007)", Integer.valueOf( 79925 ) )
			.put( "The Walking Dead (2010)", Integer.valueOf( 153021 ) )
			.put( "The West Wing (1999)", Integer.valueOf( 72521 ) )
			.put( "Tom And Jerry Tales (2006)", Integer.valueOf( 82837 ) )
			.put( "Tom Clancys Jack Ryan (2018)", Integer.valueOf( 336261 ) )
			.put( "True Blood (2008)", Integer.valueOf( 82283 ) )
			.put( "United States of Tara (2009)", Integer.valueOf( 83463 ) )
			.put( "Vikings (2013)", Integer.valueOf( 260449 ) )
			.put( "Weeds (2005)", Integer.valueOf( 74845 ) )
			.put( "Wednesday (2022)", Integer.valueOf( 397060 ) )
			.put( "Whats New Scooby-Doo (2002)", Integer.valueOf( 80119 ) )
			.build() ;

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
		for( TheTVDB_episodeClass episode : seriesEpisodesInfo.data.episodes )
		{
			log.fine( seriesEpisodesInfo.data.series.name
					+ "(" + seriesEpisodesInfo.data.series.id
					+ ") S" + episode.seasonNumber
					+ "E" + episode.number
					+ ": " + episode.name ) ;
		}

		TheTVDB_ShowInfo showInfo_TheExpanse = getFullShowInfo( Integer.valueOf( 280619 ) ) ;
		log.info( showInfo_TheExpanse.toString() ) ;
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

		// First, lookup the show name
		// For now, only shows listed in the tvShowIDs map are permitted.
		final String showName = getShowNameById( showId ) ;
		if( null == showName )
		{
			log.warning( "Unable to find showName: " + showName ) ;
			return null ;
		}
		// Post-condition: Found showName
		return getFullShowInfo( showName, showId ) ;
	}

	public TheTVDB_ShowInfo getFullShowInfo( final String showName )
	{
		assert( showName != null ) ;
		assert( !showName.isBlank() ) ;

		final Integer showId = tvShowIDs.get( showName ) ;
		if( null == showId )
		{
			log.warning( "Unable to find showId for showName: " + showName ) ;
			return null ;
		}
		// Post-condition: Found showId
		return getFullShowInfo( showName, showId ) ;
	}

	public TheTVDB_ShowInfo getFullShowInfo( final String showName, final Integer showId )
	{
		assert( showName != null ) ;
		assert( !showName.isBlank() ) ;
		assert( showId != null ) ;
		assert( showId.intValue() > 0 ) ;

		TheTVDB_ShowInfo showInfo = new TheTVDB_ShowInfo( showName, showId ) ;

		// Request information about each season until we receive an empty episodes list for that season.
		for( int seasonNumber = 1 ; seasonNumber <= 100 ; ++seasonNumber )
		{
			TheTVDB_seriesEpisodesClass seriesEpisodesInfo = getSeriesEpisodesInfo( showId.toString(), "" + seasonNumber ) ;
			if( (null == seriesEpisodesInfo)
					|| (null == seriesEpisodesInfo.data.episodes)
					|| seriesEpisodesInfo.data.episodes.isEmpty() )
			{
				log.fine( "Found empty episodes for show " + showName + " (" + showId.toString() + "), seasonNumber: " + seasonNumber ) ;
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
			//			log.info( "Found " + numEpisodes + " episode(s) for " + showName + " season #" + seasonNumber ) ;

			TheTVDB_SeasonInfo seasonInfo = new TheTVDB_SeasonInfo( seasonNumber, numEpisodes ) ;

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
			log.fine( "Added " + seasonInfo.numEpisodes + " episode(s) for show " + showName + " season " + seasonNumber ) ;

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

	public String getShowNameById( final Integer showId )
	{
		String retMe = null ;
		for( Map.Entry< String, Integer > kvPair : tvShowIDs.entrySet() )
		{
			if( kvPair.getValue().equals( showId ) )
			{
				retMe = kvPair.getKey() ;
				break ;
			}
		}
		return retMe ;
	}

	/**
	 * Return the TVDB show id for the given TV shows, or null if not found.
	 * @param showName
	 * @return
	 */
	public static Integer getTVShowID( final String showName )
	{
		assert( showName != null ) ;
		assert( !showName.isBlank() ) ;

		Integer retMe = tvShowIDs.get( showName ) ;
		return retMe ;
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
