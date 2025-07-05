package run_ffmpeg;
import java.util.List;
import java.util.logging.Logger;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;
import run_ffmpeg.ffmpeg.FFmpeg_Stream;

/**
 * I discovered some home videos that were in aspect ratios that broken Plex on multiple platforms (Roku and Android).
 * For whatever reason, Plex rejects some oddball aspect ratios but accepts many others. This class attempts to find
 * aspect ratios that are invalid to Plex. This has been trial and error -- the list below includes weird aspect ratios
 * that, for whatever reason work.
 */
public class FindBadAspectRatio
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
//	private Common common = null ;

	private MoviesAndShowsMongoDB masMDB = null ;
//	private MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_find_bad_aspect_ratio.txt" ;	
//	private static final String stopFileName = "C:\\Temp\\stop_find_bad_aspect_ratio.txt" ;
	
	protected static final double[] workingAspectRatios =
			{
					16.0 / 9.0,
					4.0 / 3.0,
					(double) 3840 / (double) 2076,
					(double) 3840 / (double) 1746,
					(double) 3840 / (double) 1610,
					(double) 2960 / (double) 2160,
					(double) 1920 / (double) 1072,
					(double) 1920 / (double) 1040,
					(double) 1920 / (double) 960,
					(double) 1920 / (double) 872,
					(double) 1920 / (double) 816,
					(double) 1920 / (double) 814,
					(double) 1920 / (double) 808,
					(double) 1920 / (double) 804,
					(double) 1920 / (double) 800,
					(double) 1904 / (double) 1072,
					(double) 1904 / (double) 1040,
					(double) 1280 / (double) 718,
					(double) 1280 / (double) 576,
					(double) 1280 / (double) 528,
					(double) 720 / (double) 576,
					(double) 720 / (double) 480,
					(double) 540 / (double) 960,
					(double) 480 / (double) 640
			} ;
	
	public FindBadAspectRatio()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
//		common = new Common( log ) ;
		masMDB = new MoviesAndShowsMongoDB( log ) ;
//		probeInfoCollection = masMDB.getProbeInfoCollection() ;
	}
	
	public static void main( String[] args )
	{
		(new FindBadAspectRatio()).execute() ;
	}
	
	public void execute()
	{
		int numBadAspectRatio = 0 ;
		
		log.info( "Getting probe info..." ) ;
		List< FFmpeg_ProbeResult > allProbeResults = masMDB.getAllProbeInfoInstances() ;
		
		for( FFmpeg_ProbeResult theResult : allProbeResults )
		{
			final FFmpeg_Stream videoStream = theResult.getVideoStream() ;
			assert( videoStream != null ) ;
			
			// Check for a bad aspect ratio
			if( !goodAspectRatio( theResult, videoStream.width, videoStream.height ) )
			{
				++numBadAspectRatio ;
				log.info( "Found invalid ratio for file: " + theResult.getFileNameWithPath() ) ;
			}
		}
		
		log.info( "Found " + numBadAspectRatio + " instance(s) of bad aspect ratio" ) ;
	}
	
	public boolean goodAspectRatio( final FFmpeg_ProbeResult theResult, final int width, final int height )
	{
		final double inputAspectRatio = (double) width / (double) height ;
		
		// Search through the common aspect ratios for a match.
		boolean foundGoodAspectRatio = false ;
		for( double aspectRatio : workingAspectRatios )
		{
			final double absDiff = Math.abs( aspectRatio - inputAspectRatio ) ;

			if( absDiff <= 0.001 )
			{
				// Good aspect ratio
				foundGoodAspectRatio = true ;
				break ;
			}
		}
		
		return foundGoodAspectRatio ;
	}
	
}
