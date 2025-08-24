package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

public class WorkflowStageThread_SubtitleTranscribe extends WorkflowStageThread
{
	protected transient MongoCollection< FFmpeg_ProbeResult > createSRTWithAICollection = null ;
	protected transient OpenAIWhisper whisper = null ;

	public WorkflowStageThread_SubtitleTranscribe( final String threadName, Logger log, Common common, MoviesAndShowsMongoDB masMDB )
	{
		super( threadName, log, common, masMDB ) ;
		createSRTWithAICollection = masMDB.getAction_CreateSRTsWithAICollection() ;
		whisper = new OpenAIWhisper( log, common ) ;
	}

	/**
	 * Override this method. This method performs the work intended for subclass instances of this class.
	 * @return true if work completed false if no work completed.
	 */
	public boolean doAction()
	{
		final FFmpeg_ProbeResult inputProbeResult = createSRTWithAICollection.findOneAndDelete( null ) ;
		if( null == inputProbeResult )
		{
			return false ;
		}
		setWorkInProgress( true ) ;
		final File inputFile = new File( inputProbeResult.getFileNameWithPath() ) ;

		// The output filename will default, as will the language file.
		// Generate an SRT via whisper ai.
		final String srtFileNameWithPath = inputFile.getAbsolutePath().replace( ".mkv", ".en.srt" ) ;

		final File srtFile = new File( srtFileNameWithPath ) ;
		if( !srtFile.exists() )
		{
			// File does not already exist -- create it.
			final String wavFileNameWithPath = inputFile.getAbsolutePath().replace( ".mkv", ".wav" ) ;
			final File wavFile = new File( wavFileNameWithPath ) ;

			final File srtFileWithWrongName = whisper.transcribeToSRT( wavFile ) ;

			// The whisperX AI outputs the srt file with the same name as the .wav file.
			// Need to change it to include ".en" in the file name
			if( srtFileWithWrongName != null )
			{
				srtFileWithWrongName.renameTo( srtFile ) ;
			}
		}

		setWorkInProgress( false ) ;
		return true ;
	}
	
	@Override
	public String getUpdateString()
	{
		return "Database size: " + createSRTWithAICollection.countDocuments() ;
	}
}
