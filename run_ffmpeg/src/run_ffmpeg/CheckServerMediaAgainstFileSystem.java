package run_ffmpeg;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.ArrayList ;
import java.util.HashSet;
import java.util.logging.Logger ;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;

import run_ffmpeg.Plex.Plex_Media;
import run_ffmpeg.Plex.Plex_MediaContainer;
import run_ffmpeg.Plex.Plex_MediaContainerWrapper;
import run_ffmpeg.Plex.Plex_Metadata;
import run_ffmpeg.Plex.Plex_Part;

public class CheckServerMediaAgainstFileSystem
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_check_server_media_against_file_system.txt" ;	
	private static final String stopFileName = "C:\\Temp\\check_server_media_against_file_system.txt" ;

	private static final String plexIPString = "192.168.1.132" ;
	private static final String plexPortString = "32400" ;
	private static final String plexToken = "4zZFMotzHstJuBxKcN3x" ;

	public CheckServerMediaAgainstFileSystem()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		(new CheckServerMediaAgainstFileSystem()).execute() ;
	}

	public void execute()
	{
		final List< Plex_Metadata > plexMovieMediaContainers = getAllMovies() ;
		log.info( "Found " + plexMovieMediaContainers.size() + " movies in Plex top level metadata" ) ;

		Set< String > plexMovieNameSet = getMovieFileNames( plexMovieMediaContainers ) ;
		log.info( "Found " + plexMovieNameSet.size() + " movies in plexMovieMediaContainers drilldown data" ) ;
		
		// Get all movie files
		final String[] extensions = { "mkv", "mp4" } ;
		final List< File > allVideoFilesInMoviesDirectory = common.getFilesInDirectoryByExtension( Common.getPathToMovies(), extensions ) ;
		log.info( "Found " + allVideoFilesInMoviesDirectory.size() + " video files in " + Common.getPathToMovies() ) ;
		
		// Prune to just movie file names
		final List< String > allMovieFileNames = getMovieFileNamesFromFiles( allVideoFilesInMoviesDirectory ) ;
		log.info( "Found " + allMovieFileNames.size() + " movie filenames in " + Common.getPathToMovies() ) ;
		
		// Presently, the drives have more movie files than the database.
		// Remove each filename found on the drive from the database Set and see what is left over.
		for( String driveMovieName : allMovieFileNames )
		{
//			log.fine( "driveMovieName: " + driveMovieName ) ;
			if( !plexMovieNameSet.remove( driveMovieName ) )
			{
				log.info( "Failed to remove driveMovieName from set: " + driveMovieName ) ;
			}
		}
		log.info( "Remaining movie names in Set: " + plexMovieNameSet.size() ) ;
		log.info( plexMovieNameSet.toString() ) ;
	}
	
	public List< String > getMovieFileNamesFromFiles( final List< File > allVideoFilesInMoviesDirectory )
	{
		List< String > movieFileNames = new ArrayList< String >() ;
		final Pattern movieFileNamePattern = Pattern.compile( "(?<movieName>.*)\\((?<year>[\\d]{4})\\).*\\.(?<extension>(mkv|mp4))" ) ;

		for( File theVideoFile : allVideoFilesInMoviesDirectory )
		{
			final String fileName = theVideoFile.getName() ;
			final Matcher movieNameMatcher = movieFileNamePattern.matcher( fileName ) ;
			if( movieNameMatcher.find() )
			{
				// Found a match
				movieFileNames.add( fileName ) ;
			}
		}
		return movieFileNames ;
	}

	public Set< String > getMovieFileNames( final List< Plex_Metadata > movieMediaContainers )
	{
		Set< String > movieNameSet = new HashSet< String >() ;

		// Build a list of the movie file names
		final Pattern movieFileNamePattern = Pattern.compile( "(?<movieName>.*)\\((?<year>[\\d]{4})\\).*\\.(?<extension>(mkv|mp4))" ) ;
		
		for( Plex_Metadata metadata : movieMediaContainers )
		{
			int numMoviesInThisMetadata = 0 ;
			for( Plex_Media media : metadata.Media )
			{
				boolean foundOnePartAlready = false ;
				for( Plex_Part part : media.Part )
				{
					// Check if this filename is a match.
					// The part.file filename stored in the Plex database includes the full path.
					// Use the File constructor to strip out the path.
					final String fileNameWithPath = part.file ;
					final File theFile = new File( fileNameWithPath ) ;
					final String fileName = theFile.getName() ;

					final Matcher fileNameMatcher = movieFileNamePattern.matcher( fileName ) ;
					if( !fileNameMatcher.find() )
					{
						// No match.
						continue ;
					}
					// PC: Found a filename match
					++numMoviesInThisMetadata ;
					
					if( numMoviesInThisMetadata > 1 )
					{
						log.info( "Part filename: " + fileName ) ;
					}
					
					if( foundOnePartAlready )
					{
						log.info( "Found another Part for media: " + media.toString() ) ;
					}
					foundOnePartAlready = true ;
					
					// Trim off any leading folders
					final File movieFile = new File( fileName ) ;
					
					movieNameSet.add( movieFile.getName() ) ;
					log.fine( "Found movie name: " + fileName ) ;
				}
				foundOnePartAlready = false ;
			}
		}
		return movieNameSet ;
	}

	public List< Plex_Metadata > getAllMovies()
	{
		List< Plex_Metadata > allMovieMetaDatas = new ArrayList< Plex_Metadata >() ;

		final String uri = "http://"
				+ getPlexIPString()
				+ ":"
				+ getPlexPortString()
				+ "/library/sections/1/all"
				+ "?X-Plex-Token="
				+ getPlexToken() ;

		final String responseBody = httpGet( uri ) ;
		if( responseBody.isEmpty() )
		{
			log.warning( "Failed to get response for query: " + uri ) ;
			return allMovieMetaDatas ;
		}
		// Successful response

		Gson queryResponseGson = new Gson() ;
		final Plex_MediaContainerWrapper mediaContainerWrapper = queryResponseGson.fromJson( responseBody, Plex_MediaContainerWrapper.class ) ;
		final Plex_MediaContainer mediaContainer = mediaContainerWrapper.MediaContainer ;

		allMovieMetaDatas.addAll( mediaContainer.Metadata ) ;

		return allMovieMetaDatas ;
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
					//					.header( "Authorization", "X-Plex-Token " + getPlexToken() )
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

	public static String getLogFileName()
	{
		return logFileName ;
	}

	public static String getStopFileName()
	{
		return stopFileName ;
	}

	public static String getPlexIPString()
	{
		return plexIPString ;
	}

	public static String getPlexPortString()
	{
		return plexPortString ;
	}

	public static String getPlexToken()
	{
		return plexToken ;
	}
}
