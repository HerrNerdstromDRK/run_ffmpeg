package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

public class CreateSRTsWithAI_CreateSRTs
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	private MoviesAndShowsMongoDB masMDB = null ;
	
	/// File name to which to log activities for this application.
	private static final String logFileName = "log_create_srts_with_ai_create_srts.txt" ;
	private static final String stopFileName = "C:\\Temp\\stop_crate_srts_with_ai.txt" ;
	
	public CreateSRTsWithAI_CreateSRTs()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		masMDB = new MoviesAndShowsMongoDB( log ) ;
	}
	
	public static void main( String[] args )
	{
		(new CreateSRTsWithAI_CreateSRTs()).execute() ;
	}
	
	public void execute()
	{
		MongoCollection< FFmpeg_ProbeResult > createSRTsHandle = masMDB.getAction_CreateSRTsWithAICollection() ;
		
		while( shouldKeepRunning() )
		{
			final FFmpeg_ProbeResult theProbeResult = createSRTsHandle.findOneAndDelete( null ) ;
			if( null == theProbeResult )
			{
				// No more work to do.
				log.info( "Out of work to do." ) ;
				break ;
			}
			
			processWorkObject( theProbeResult ) ;
			
			// For testing -- just do one
			break ;
		}
	}
	
	public void processWorkObject( final FFmpeg_ProbeResult theProbeResult )
	{
		// Steps:
		// 1) Extract a .wav file for the given input file
		// 2) Transcribe the .wav file
		// 3) Move/delete the temporary files
		
		// 1) Extract a .wav file
		final String wavOutputFileNameWithPath = Common.replaceExtension( theProbeResult.getFileNameWithPath(), "wav" ) ;
		final File inputFile = new File( theProbeResult.getFileNameWithPath() ) ;
		final File wavOutputFile = new File( wavOutputFileNameWithPath ) ;
		
		log.info( "Extracting audio from " + inputFile.getAbsolutePath() + " to " + wavOutputFile.getAbsolutePath() ) ;
		boolean extractAudioSuccess = common.extractAudioFromAVFile( inputFile, wavOutputFile ) ;
		if( !extractAudioSuccess )
		{
			log.warning( "Failed to extract audio from " + inputFile.getAbsolutePath() + " to " + wavOutputFile.getAbsolutePath() ) ;
			return ;
		}

		// 2) Transcribe
		OpenAIWhisper whisper = new OpenAIWhisper( log, common ) ;
		whisper.transcribeToSRT( wavOutputFile ) ;
		// whisperFile should have the same semantic name as the original input file but ending with .srt
		
	}
	
	public String getStopFileName()
	{
		return stopFileName ;
	}
	
	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( getStopFileName() ) ;
	}
}
