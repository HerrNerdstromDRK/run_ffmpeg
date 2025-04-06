package run_ffmpeg;

import java.io.File;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class RMI_Transcode_Client
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_rmi_transcode_client.txt" ;

	public RMI_Transcode_Client()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		(new RMI_Transcode_Client()).execute() ;
	}

	public void execute()
	{
		try
		{
			Registry registry = LocateRegistry.getRegistry( "localhost", 12345 ) ;

			final String fileNameWithPath = "\\\\skywalker\\Media\\TV_Shows\\Planet Earth (2006)\\Season 01\\Planet Earth - S01E01 - From Pole To Pole.mkv" ;
			FFmpegProbeResult probeResult = common.ffprobeFile( new File( fileNameWithPath ), log ) ;
			if( null == probeResult )
			{
				log.warning( "Null probeResult for file " + fileNameWithPath ) ;
				return ;
			}
			if( (null == probeResult.streams) || probeResult.streams.isEmpty() )
			{
				log.warning( "null or empty list of streams for: " + probeResult.toString() ) ;
				return ;
			}
			FFmpegStream videoStream = probeResult.streams.get( 0 ) ;
			if( null == videoStream )
			{
				log.warning( "null videoStream for: " + probeResult.toString() ) ;
				return ;
			}
			if( !videoStream.codec_type.equalsIgnoreCase( "video" ) )
			{
				log.warning( "Stream 0 is not video: " + probeResult.toString() ) ;
				return ;
			}
			if( videoStream.tags.isEmpty() )
			{
				log.warning( "tags is empty for: " + probeResult.toString() ) ;
				return ;
			}
			final String durationEngString = videoStream.tags.get( "DURATION-eng" ) ;
			if( null == durationEngString )
			{
				log.warning( "DURATION-eng not found in: " + probeResult.toString() ) ;
				return ;
			}
			// DURATION-eng should be of the form: "hrs:mins:secs.subsec"
			final String[] durationEngStringParts = durationEngString.split( ":" ) ;
			if( durationEngStringParts.length != 3 )
			{
				log.warning( "Invalid number of parts for DURATION-eng: " + probeResult.toString() ) ;
				return ;
			}
			final int durationHours = Integer.parseInt( durationEngStringParts[ 0 ] ) ;
			final int durationMinutes = Integer.parseInt( durationEngStringParts[ 1 ] ) ;
			final int durationSeconds = Integer.parseInt( durationEngStringParts[ 2 ].substring( 0, 2 ) ) ;

			final int durationTotalInSeconds = (durationHours * 3600) + (durationMinutes * 60) + durationSeconds ;

			final int durationPerPartInSeconds = 10 ;
			List< Integer > startTimesInSeconds = new ArrayList< Integer >() ;
			for( int startTimeInSeconds = 0 ; startTimeInSeconds <= durationTotalInSeconds ; startTimeInSeconds += durationPerPartInSeconds )
			{
				// Not sure what will happen with the last block
				startTimesInSeconds.add( Integer.valueOf( startTimeInSeconds ) ) ;
			}
			log.info( "Number of parts: " + startTimesInSeconds.size() ) ;			

			RMI_Transcode_Server_Interface serverImpl = (RMI_Transcode_Server_Interface) registry.lookup( "RMI_Transcode_Server") ;
			log.info( "Calling transcodeFilePart()" ) ;

			for( Integer startTime : startTimesInSeconds )
			{
				boolean success = serverImpl.transcodeFilePart( fileNameWithPath, startTime, durationPerPartInSeconds ) ;
				log.info( "(" + fileNameWithPath + ", " + startTime + ", " + durationPerPartInSeconds + "): " + success ) ;
			}
		}
		catch( Exception theException )
		{
			log.info( "Exception: " + theException.toString() ) ;
		}
	}
}
