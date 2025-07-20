package run_ffmpeg;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
		final String showDirectoryPath = "\\\\skywalker\\Media\\To_OCR\\Boardwalk Empire (2010) {imdb-0979432}" ;
		
		// Note that the imdb ID must remain a string since the preceding 0 is important.
		final String imdbShowIDString = getIMDBShowIDFromPath( showDirectoryPath ) ;
		if( null == imdbShowIDString )
		{
			return ;
		}
		
		try
		{
			// Iterate on each season
			final File showDirectoryFile = new File( showDirectoryPath ) ;
			final File[] fileList = showDirectoryFile.listFiles() ;
			for( File theFile : fileList )
			{
				if( !theFile.isDirectory() )
				{
					continue ;
				}
				final Pattern seasonPattern = Pattern.compile( ".*\\Season (?<seasonNumber>[\\d]+)" ) ;
				final Matcher seasonMatcher = seasonPattern.matcher( theFile.getAbsolutePath() ) ;
				if( !seasonMatcher.find() )
				{
					log.warning( "Unable to find season in file path: " + theFile.getAbsolutePath() ) ;
					continue ;
				}
				final String seasonNumberString = seasonMatcher.group( "seasonNumber" ) ;
				final Integer seasonNumberInteger = Integer.valueOf( seasonNumberString ) ;
				log.info( "Found season number " + seasonNumberInteger.intValue() + " for directory " + theFile.getAbsolutePath() ) ;
				
				// Read all information I can about this season
				// totalNumPages will be adjusted after reading the first page
				int totalNumPages = 2 ;
				List< OpenSubtitles_Data > seasonData = new ArrayList< OpenSubtitles_Data >() ;
				for( int pageNumber = 1 ; pageNumber <= totalNumPages ; ++pageNumber )
				{
					log.info( "Getting page " + pageNumber ) ;
					final OpenSubtitles_SubtitlesResponse stResponse = searchForSubtitlesByIMDBID( imdbShowIDString,
							seasonNumberString,
							"", // episode number
							pageNumber ) ;
					if( null == stResponse )
					{
						log.warning( "null stResponse for " + showDirectoryPath + " page " + pageNumber ) ;
						totalNumPages = -1 ; // Let the code outside of the loop know the algorithm failed.
						break ;
					}
					seasonData.addAll( stResponse.getData() ) ;
					
					// Update total number of pages
					totalNumPages = stResponse.getTotalPages().intValue() ;
				}
				log.info( "Got " + totalNumPages + " page(s) of data for " + showDirectoryPath ) ;
				
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}

		//		getShowSubtitles( )

		//		final String imdbEpisodeID = "1276201" ;
		//		findEpisodeSubtitles( imdbEpisodeID ) ;

	}

	public String getIMDBShowIDFromPath( final String showDirectoryPath )
	{
		assert( showDirectoryPath != null ) ;

		final Pattern pathPattern = Pattern.compile( ".*\\{imdb-(?<imdbShowID>[\\d]+)\\}.*" ) ;
		final Matcher pathMatcher = pathPattern.matcher( showDirectoryPath ) ;
		if( !pathMatcher.find() )
		{
			log.warning( "Unable to find IMDB id in path: " + showDirectoryPath ) ;
			return null ;
		}
		// PC: Got a valid string for IMDB id
		final String imdbShowID = pathMatcher.group( "imdbShowID" ) ;
		log.info( "imdbShowID: " + imdbShowID ) ;

		return imdbShowID ;
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
		if( subtitlesResponse.getData().isEmpty() )
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
	 * Return data for an imdb show or an episode. If the episode id string is empty then the show information will be returned. The show id string can
	 *  be empty or filled and it should work appropriately.
	 * @param imdbShowIDString The imdb id of the show; can be empty but not null
	 * @param seasonNumber The number of the season; can be empty but not null
	 * @param imdbEpisodeIDString The imdb episode id; can be empty but not null.
	 * @param pageNumber Minimum value of 1
	 * @return
	 */
	public OpenSubtitles_SubtitlesResponse searchForSubtitlesByIMDBID( final String imdbShowIDString,
			final String seasonNumber,
			final String imdbEpisodeIDString,
			final int pageNumber )
	{
		assert( imdbShowIDString != null ) ;
		assert( seasonNumber != null ) ;
		assert( imdbEpisodeIDString != null ) ;
		
		String uri = getBaseURI() + "/subtitles?languages=en" ;
		if( !imdbShowIDString.isBlank() )
		{
			uri += "&parent_imdb_id=" + imdbShowIDString ;
		}
		if( !seasonNumber.isBlank() )
		{
			// Strip off the leading 0 for seasons less than 10
			final Integer seasonNumberInteger = Integer.valueOf( seasonNumber ) ;
			uri += "&season_number=" + seasonNumberInteger.intValue() ;
		}
		if( !imdbEpisodeIDString.isBlank() )
		{
			uri += "&imdb_id=" + imdbEpisodeIDString ;
		}
		uri += "&page=" + pageNumber ;

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
