package run_ffmpeg.OpenSubtitles;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import run_ffmpeg.Common;
import run_ffmpeg.FileNamePattern;

public class OpenSubtitles
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

	protected long timeOfNextDownloadAllowed = 0 ;

	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_opensubtitles.txt" ;

	public OpenSubtitles()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		login() ;
	}

	public OpenSubtitles( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;

		login() ;
	}

	public static void main( String[] args )
	{
		(new OpenSubtitles()).execute() ;
	}

	protected void execute()
	{
		common.setTestMode( false ) ;

		//final String imdbEpisodeID = "1276201" ;
		//findEpisodeSubtitles( imdbEpisodeID ) ;

		//		testDownloadShowSubtitleInformation() ;
		testSearchRequest() ;
	}

	protected void testDownloadShowSubtitleInformation()
	{
		final String showDirectoryPath = "\\\\skywalker\\Media\\To_OCR\\Boardwalk Empire (2010) {imdb-0979432}" ;

		// Note that the imdb ID must remain a string since the preceding 0 is important.
		final String imdbShowIDString = FileNamePattern.getIMDBShowID( showDirectoryPath ) ;
		if( null == imdbShowIDString )
		{
			log.warning( "Unable to get imdbShowIDString from showDirectoryPath: " + showDirectoryPath ) ;
			return ;
		}

		// Iterate on each season
		final File showDirectoryFile = new File( showDirectoryPath ) ;
		final File[] fileList = showDirectoryFile.listFiles() ;
		for( File theFile : fileList )
		{
			if( !theFile.isDirectory() )
			{
				continue ;
			}

			final int seasonNumber = FileNamePattern.getShowSeasonNumber( theFile ) ;

			List< OpenSubtitles_Data > seasonSubtitleData = getSubtitleInfoForShowSeason( imdbShowIDString, seasonNumber ) ;
			//			log.info( "Get info for season " + seasonNumberInteger + ":" ) ;
			//			for( OpenSubtitles_Data theData : seasonSubtitleData )
			//			{
			//				log.info( theData.toString() ) ;
			//			}
			List< OpenSubtitles_Data > subtitleIDsToDownload = findBestSubtitleFileIDsToDownloadForSeason( seasonSubtitleData ) ;
			if( !subtitleIDsToDownload.isEmpty() )
			{
				//final File subtitleFile = 
				downloadSubtitleFileByID( subtitleIDsToDownload.getFirst().getAttributes().getFiles().getFirst().getFile_id().toString(), theFile ) ;
			}
		}
	}

	protected void testSearchRequest()
	{
		// Test search for subtitle information using json objects.
		OpenSubtitles_SearchRequest searchRequest = new OpenSubtitles_SearchRequest() ;
		searchRequest.parent_imdb_id = Integer.valueOf( "0979432" ) ;
		searchRequest.season_number = Integer.valueOf( 2 ) ;
		searchRequest.episode_number = Integer.valueOf( 12 ) ;

		Gson searchRequestGson = new Gson() ;
		final String searchRequestJson = searchRequestGson.toJson( searchRequest ) ;
		log.info( "searchRequestJson: " + searchRequestJson ) ;

		CloseableHttpClient httpClient = HttpClients.createDefault() ;
		HttpGet httpGet = new HttpGet( getBaseURI() + "/subtitles" ) ;

		httpGet.setHeader( HttpHeaders.CONTENT_TYPE, "application/json" ) ;
		httpGet.setHeader( HttpHeaders.ACCEPT, "*/*" ) ;
		httpGet.setHeader( HttpHeaders.AUTHORIZATION, "Bearer " + getAPIToken() ) ;
		httpGet.setHeader( "Api-Key", getApiKey() ) ;
		httpGet.setHeader( HttpHeaders.USER_AGENT, getUserAgent() ) ;
		
		List< NameValuePair > params = new ArrayList< NameValuePair >() ;
		params.add( new BasicNameValuePair( "parent_imdb_id", "979432" ) ) ;
		params.add( new BasicNameValuePair( "season_number", "2" ) ) ;
		params.add( new BasicNameValuePair( "episode_number", "12" ) ) ;
		httpGet.setEntity( new UrlEncodedFormEntity( params ) ) ;

		try
		{
//			CloseableHttpResponse response = httpClient.execute( httpPost ) ;
			final String responseBody = httpClient.execute( httpGet, response -> {
				return EntityUtils.toString( response.getEntity() ) ;
			}) ;
			log.info( "responseBody: " + responseBody ) ;
			
//			final int statusCode = response.getCode() ;
//			final String responseBody = EntityUtils.toString( response.getEntity() ) ;

			//			HttpClient client = HttpClient.newHttpClient() ;
			//			HttpRequest request = HttpRequest.newBuilder()
			//					.header( "Accept", "application/json" )
			//					.header( "Content-Type", "application/json" )
			//					.header( "Api-Key",  getApiKey() )
			//					.header( "Authorization", "Bearer " + getAPIToken() )
			//					.header( "User-Agent",  getUserAgent() )
			//					.uri( URI.create( getBaseURI() + "/subtitles" ) )
			//					.POST( HttpRequest.BodyPublishers.ofString( searchRequestJson ) )
			//					.build() ;
			//			log.info( "Sending HttpRequest: " + request.toString() ) ;
			//			HttpResponse< String > response = client.send( request, HttpResponse.BodyHandlers.ofString() ) ;
			//
			//			final int responseCode = response.statusCode() ;
			//			final String responseBody = response.body() ;
			//
			//			log.info( "responseCode: " + responseCode + ", responseBody: " + responseBody ) ;

			//			Gson downloadResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
			//			final OpenSubtitles_DownloadResponse downloadResponse = downloadResponseGson.fromJson( response.body(), OpenSubtitles_DownloadResponse.class ) ;
			//			log.info( "downloadResponse: " + downloadResponseGson.toJson( downloadResponse ).toString() ) ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}

	/**
	 * Download the subtitle file with the given ID.
	 * @param subtitleFileID
	 * @return null if unsuccessful, or the File, already written to disk, if successful.
	 */
	public File downloadSubtitleFileByID( final String subtitleFileID, final File outputFile )
	{
		assert( subtitleFileID != null ) ;
		assert( !subtitleFileID.isBlank() ) ;

		// Downloading the file is a two step process:
		// 1) Request download link
		// 2) Download file from requested link
		final OpenSubtitles_DownloadRequest downloadRequest = new OpenSubtitles_DownloadRequest( subtitleFileID ) ;

		Gson downloadRequestGson = new Gson() ;
		final String downloadRequestJson = downloadRequestGson.toJson( downloadRequest ) ;
		log.info( "downloadRequestJson: " + downloadRequestJson ) ;

		try
		{
			HttpClient client = HttpClient.newHttpClient() ;
			HttpRequest request = HttpRequest.newBuilder()
					.header( "Accept", "application/json" )
					.header( "Content-Type", "application/json" )
					.header( "Api-Key",  getApiKey() )
					.header( "Authorization", "Bearer " + getAPIToken() )
					.header( "User-Agent",  getUserAgent() )
					.uri( URI.create( getBaseURI() + "/download" ) )
					.POST( HttpRequest.BodyPublishers.ofString( downloadRequestJson ) )
					.build() ;
			log.info( "Sending HttpRequest: " + request.toString() ) ;
			HttpResponse< String > response = client.send( request, HttpResponse.BodyHandlers.ofString() ) ;

			final int responseCode = response.statusCode() ;
			final String responseBody = response.body() ;

			log.info( "responseCode: " + responseCode + ", responseBody: " + responseBody ) ;

			Gson downloadResponseGson = new GsonBuilder().setPrettyPrinting().create() ;
			final OpenSubtitles_DownloadResponse downloadResponse = downloadResponseGson.fromJson( response.body(), OpenSubtitles_DownloadResponse.class ) ;
			log.info( "downloadResponse: " + downloadResponseGson.toJson( downloadResponse ).toString() ) ;

			if( 406 == responseCode )
			{
				// No more downloads available.
				// Record the time of next available download.
				// Expected response UTC is of the form: 2025-07-26T23:59:59.000Z
				log.info( "Exceeded max download quota" ) ;

				final Instant now = Instant.now() ;
				final String resetTimeUTCString = downloadResponse.getReset_time_utc() ;
				final Instant resetTimeUTCInstant = Instant.parse( resetTimeUTCString ) ;
				final Duration durationToResetTimeUTC = Duration.between( now,  resetTimeUTCInstant ) ;

				setTimeOfNextDownloadAllowed( System.currentTimeMillis() + durationToResetTimeUTC.toMillis() ) ;	
			}

			if( responseCode != 200 )
			{
				// Login unsuccessful
				log.warning( "Request for download file id failed. responseCode: " + responseCode ) ;
				return null ;
			}
			// Request for download successful

			final String downloadLink = downloadResponse.getLink() ;
			final URI theURI = new URI( downloadLink ) ;
			final URL theURL = theURI.toURL() ;

			log.info( "Copying " + theURL.toString() + " to " + outputFile.getAbsolutePath() ) ;
			FileUtils.copyURLToFile( theURL, outputFile ) ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
			return null ;
		}
		return outputFile ;
	}

	public List< File > downloadSubtitlesForShow( final String imdbShowIDString, final int seasonNumber, final File outputDir )
	{
		assert( imdbShowIDString != null ) ;
		assert( outputDir != null ) ;

		// First, get all subtitle information for this show and season
		final List< OpenSubtitles_Data > allSubtitlesForSeason = getSubtitleInfoForShowSeason( imdbShowIDString, seasonNumber ) ;
		assert( allSubtitlesForSeason != null ) ;

		// Next, find the best subtitles for each episode
		final List< OpenSubtitles_Data > subtitleDataToDownload = findBestSubtitleFileIDsToDownloadForSeason( allSubtitlesForSeason ) ;
		assert( subtitleDataToDownload != null ) ;

		List< File > downloadedSubtitleFiles = new ArrayList< File >() ;
		for( OpenSubtitles_Data subtitleData : subtitleDataToDownload )
		{
			File subtitleFile = downloadSubtitleFileByID( subtitleData.getAttributes().getFiles().getFirst().getFile_id().toString(), outputDir ) ;
			if( subtitleFile != null )
			{
				downloadedSubtitleFiles.add( subtitleFile ) ;
			}
		}
		return downloadedSubtitleFiles ;
	}

	/**
	 * Return the file IDs of the subtitle files to download.
	 * @param seasonSubtitleData
	 * @return
	 */
	public List< OpenSubtitles_Data > findBestSubtitleFileIDsToDownloadForSeason( final List< OpenSubtitles_Data > seasonSubtitleData )
	{
		assert( seasonSubtitleData != null ) ;

		// Find all of the episode numbers.
		Set< Integer > episodeNumbers = new HashSet< Integer >() ;
		for( OpenSubtitles_Data theData : seasonSubtitleData )
		{
			if( (theData.getAttributes() != null)
					&& (theData.getAttributes().getFeature_details() != null)
					&& (theData.getAttributes().getFeature_details().getEpisode_number() != null))
			{
				episodeNumbers.add( theData.getAttributes().getFeature_details().getEpisode_number() ) ;
			}
		}
		String slugInfo = "(Empty)" ;
		if( !seasonSubtitleData.isEmpty() )
		{
			slugInfo = "(" + seasonSubtitleData.getFirst().getAttributes().getSlug() + ")" ;
		}
		log.info( slugInfo + " Episode numbers: " + episodeNumbers.toString() ) ;

		// Find the subtitle with highest download count for each episode
		List< OpenSubtitles_Data > subtitleIDsToDownload = new ArrayList< OpenSubtitles_Data >() ;
		for( Integer episodeNumberInteger : episodeNumbers )
		{
			subtitleIDsToDownload.add( getSubtitleIDWithHighestDownloadCountForEpisode( seasonSubtitleData, episodeNumberInteger.intValue() ) ) ;
		}
		return subtitleIDsToDownload ;
	}

	public OpenSubtitles_Data getSubtitleIDWithHighestDownloadCountForEpisode( final List< OpenSubtitles_Data > seasonSubtitleData, final int episodeNumber )
	{
		assert( seasonSubtitleData != null ) ;
		OpenSubtitles_Data subtitleDataWithHighestDownloadCount = null ;

		int highestDownloadCount = -1 ;
		for( OpenSubtitles_Data theData : seasonSubtitleData )
		{
			if( null == theData.getAttributes() )
			{
				log.warning( "null attributes for data: " + theData.toString() ) ;
				continue ;
			}
			if( null == theData.getAttributes().getDownload_count() )
			{
				log.warning( "null download_count for data: " + theData.toString() ) ;
				continue ;
			}
			if( null == theData.getAttributes().getFeature_details() )
			{
				log.warning( "null feature_details for data: " + theData.toString() ) ;
				continue ;
			}
			if( null == theData.getAttributes().getFeature_details().getEpisode_number() )
			{
				log.warning( "null episode_number for data: " + theData.toString() ) ;
				continue ;
			}
			if( theData.getAttributes().getFeature_details().getEpisode_number().intValue() != episodeNumber )
			{
				// Wrong episode
				continue ;
			}
			if( null == theData.getAttributes().getFiles() )
			{
				log.warning( "null files for data: " + theData.toString() ) ;
				continue ;
			}
			if( theData.getAttributes().getFiles().isEmpty() )
			{
				log.warning( "empty files for data: " + theData.toString() ) ;
				continue ;
			}
			if( null == theData.getAttributes().getFiles().getFirst().getFile_id() )
			{
				log.warning( "null file_id for data: " + theData.toString() ) ;
				continue ;
			}
			// PC: All elements are non-null and this is the correct episode number 
			if( theData.getAttributes().getDownload_count().intValue() > highestDownloadCount )
			{
				// New highest download count for this episode.
				subtitleDataWithHighestDownloadCount = theData ;
				highestDownloadCount = theData.getAttributes().getDownload_count() ;

				log.fine( "Found new highest download count for episode " + episodeNumber + ": " + highestDownloadCount ) ;
			}
		}

		return subtitleDataWithHighestDownloadCount ;
	}

	public void getSubtitleInfoForShow( final String imdbShowIDString )
	{
		assert( imdbShowIDString != null ) ;
		assert( !imdbShowIDString.isBlank() ) ;

	}

	public List< OpenSubtitles_Data > getSubtitleInfoForShowSeason( final String imdbShowIDString, final int seasonNumber )
	{
		assert( imdbShowIDString != null ) ;
		assert( !imdbShowIDString.isBlank() ) ;

		final String seasonNumberString = Integer.toString( seasonNumber ) ;
		List< OpenSubtitles_Data > seasonData = new ArrayList< OpenSubtitles_Data >() ;
		try
		{
			// Read all information I can about this season
			// totalNumPages will be adjusted after reading the first page
			int totalNumPages = 1 ;
			for( int pageNumber = 1 ; pageNumber <= totalNumPages ; ++pageNumber )
			{
				log.info( "Getting page " + pageNumber ) ;
				final OpenSubtitles_SubtitlesResponse stResponse = searchForSubtitlesByIMDBID( imdbShowIDString,
						seasonNumberString,
						"", // episode number
						pageNumber ) ;
				if( null == stResponse )
				{
					log.warning( "null stResponse for imdb show " + imdbShowIDString + " page " + pageNumber ) ;
					totalNumPages = -1 ; // Let the code outside of the loop know the algorithm failed.
					break ;
				}
				seasonData.addAll( stResponse.getData() ) ;

				// Update total number of pages
				if( stResponse.getTotal_pages() != null )
				{
					totalNumPages = stResponse.getTotal_pages().intValue() ;
				}
			}
			log.info( "Got " + totalNumPages + " page(s) of data for imdb show " + imdbShowIDString ) ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
		return seasonData ;
	}

	public void findEpisodeSubtitles( final String imdbEpisodeIDString )
	{
		final OpenSubtitles_SubtitlesResponse subtitlesResponse = searchForSubtitlesByIMDBID( "", // show ID
				"", // season number
				imdbEpisodeIDString,
				1 // page number
				) ;
		if( null == subtitlesResponse )
		{
			log.warning( "null subtitlesResponse searching for imdb id " + imdbEpisodeIDString ) ;
		}
		if( (null == subtitlesResponse.getData()) || subtitlesResponse.getData().isEmpty() )
		{
			log.warning( "Found empty data for imdb id " + imdbEpisodeIDString ) ;
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

	/**
	 * Return data for an imdb show or an episode. If the episode number string is empty then the season information will be returned.
	 * @param imdbShowIDString The imdb id of the show (parent to any episode) or movie.
	 * @param seasonNumber The number of the season.
	 * @param episodeNumber The season episode number; can be empty but not null.
	 * @param pageNumber Minimum value of 1
	 * @return
	 */
	public OpenSubtitles_SubtitlesResponse searchForSubtitlesByIMDBID( final String imdbShowIDString,
			final String seasonNumber,
			final String episodeNumber,
			final int pageNumber )
	{
		assert( imdbShowIDString != null ) ;
		assert( seasonNumber != null ) ;
		assert( episodeNumber != null ) ;

		String uri = getBaseURI() + "/subtitles?" ;
		uri += "&episode_number=" + episodeNumber ;
		uri += "&languages=en" ;

		final Integer imdbShowIDInteger = Integer.valueOf( imdbShowIDString ) ;
		uri += "&parent_imdb_id=" + imdbShowIDInteger.intValue() ;

		// Strip off the leading 0 for seasons less than 10
		final Integer seasonNumberInteger = Integer.valueOf( seasonNumber ) ;
		uri += "&season_number=" + seasonNumberInteger.intValue() ;

		if( pageNumber > 1 )
		{
			uri += "&page=" + pageNumber ;
		}

		final String responseBody = httpGet( uri ) ;
		if( responseBody.isEmpty() )
		{
			log.warning( "Failed to get response for query: " + uri ) ;
			return null ;
		}
		
//		String uri = getBaseURI() + "/subtitles?languages=en" ;
//		if( pageNumber > 1 )
//		{
//			uri += "&page=" + pageNumber ;
//		}
//		if( !imdbShowIDString.isBlank() )
//		{
//			final Integer imdbShowIDInteger = Integer.valueOf( imdbShowIDString ) ;
//			uri += "&parent_imdb_id=" + imdbShowIDInteger.intValue() ;
//		}
//		if( !seasonNumber.isBlank() )
//		{
//			// Strip off the leading 0 for seasons less than 10
//			final Integer seasonNumberInteger = Integer.valueOf( seasonNumber ) ;
//			uri += "&season_number=" + seasonNumberInteger.intValue() ;
//		}
//		if( !episodeNumber.isBlank() )
//		{
//			uri += "&episode_number=" + episodeNumber ;
//		}
//
//		final String responseBody = httpGet( uri ) ;
//		if( responseBody.isEmpty() )
//		{
//			log.warning( "Failed to get response for query: " + uri ) ;
//			return null ;
//		}
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
					.header( "Content-Type", "application/json" )
					.header( "Accept", "*/*" )
					.header( "Authorization", "Bearer " + getAPIToken() )
					.header( "Api-Key", getApiKey() )
					.header( "User-Agent",  getUserAgent() )
					.uri( URI.create( uri ) )
					.GET()
					.build() ;
			log.info( "Sending HttpRequest: " + request.toString() ) ;
			HttpResponse< String > response = client.send( request, HttpResponse.BodyHandlers.ofString() ) ;
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
				log.warning( "responseCode " + responseCode + " for query " + uri + ", response: " + response.toString() ) ;
				log.warning( "response.headers: " + response.headers().toString() ) ;
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

	public long getTimeOfNextDownloadAllowed() {
		return timeOfNextDownloadAllowed;
	}

	public void setTimeOfNextDownloadAllowed(long timeOfNextDownloadAllowed) {
		this.timeOfNextDownloadAllowed = timeOfNextDownloadAllowed;
	}

	public boolean isDownloadAllowed()
	{
		return (System.currentTimeMillis() > getTimeOfNextDownloadAllowed()) ;
	}
}
