package run_ffmpeg.Plex;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;

import run_ffmpeg.Common;

public class Plex
{
	protected final String plexIPString = "192.168.1.132" ;
	protected final String plexPortString = "32400" ;
	protected final String plexToken = "4zZFMotzHstJuBxKcN3x" ;
	
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_plex.txt" ;
	
	public Plex()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public Plex( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;
	}
	
	public static void main( String[] args )
	{
		(new Plex()).execute() ;
	}
	
	public void execute()
	{
		
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
	
	public String getPlexIPString()
	{
		return plexIPString ;
	}

	public String getPlexPortString()
	{
		return plexPortString ;
	}

	public String getPlexToken()
	{
		return plexToken ;
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
}
