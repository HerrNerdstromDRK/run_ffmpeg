package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

public class WorkflowStageThread_SubtitleTranscribe extends WorkflowStageThread
{
	protected transient Logger log = null ;
	protected transient Common common = null ;
	protected transient MoviesAndShowsMongoDB masMDB = null ;
	protected transient MongoCollection< FFmpeg_ProbeResult > createSRTCollection = null ;
	protected transient OpenAIWhisper whisper = null ;

	public WorkflowStageThread_SubtitleTranscribe( final String threadName, Logger log, Common common, MoviesAndShowsMongoDB masMDB )
	{
		super( threadName, log, common, masMDB ) ;
		createSRTCollection = masMDB.getAction_CreateSRTsWithAICollection() ;
		whisper = new OpenAIWhisper( log, common ) ;
	}

	/**
	 * Override this method. This method performs the work intended for subclass instances of this class.
	 * @return true if work completed false if no work completed.
	 */
	public boolean doAction()
	{
		final FFmpeg_ProbeResult inputProbeResult = createSRTCollection.findOneAndDelete( null ) ;
		if( null == inputProbeResult )
		{
			return false ;
		}
		final File inputFile = new File( inputProbeResult.getFileNameWithPath() ) ;
		
		// The output filename will default, as will the language file.
		// Generate an SRT via whisper ai.
		final String srtFileNameWithPath = inputFile.getAbsolutePath().replace( ".mkv", ".en.srt" ) ;

		File srtFile = new File( srtFileNameWithPath ) ;
		if( !srtFile.exists() )
		{
			// File does not already exist -- create it.
			final String wavFileNameWithPath = inputFile.getAbsolutePath().replace( ".mkv", ".wav" ) ;
			final File wavFile = new File( wavFileNameWithPath ) ;
			
			srtFile = whisper.transcribeToSRT( wavFile ) ;
		}

		return true ;
	}
}
