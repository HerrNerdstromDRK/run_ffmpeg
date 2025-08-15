package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

public class Subtitles_TranscribeWorkerThread extends run_ffmpegWorkerThread
{
	/// Reference back to the controller thread.
	private transient Subtitles_Transcribe theController = null ;
	
	private transient OpenAIWhisper whisper = null ;
	
	public Subtitles_TranscribeWorkerThread( Subtitles_Transcribe theController,
			Logger log,
			Common common )
	{
		super( log, common ) ;
		
		assert( theController != null ) ;

		this.theController = theController ;
		whisper = new OpenAIWhisper( log, common ) ;
	}

	@Override
	public void run()
	{
		log.info( getName() + " New thread reporting for duty." ) ;
		setWorkInProgress( false ) ;
		
		while( shouldKeepRunning() )
		{
			// Get a file to transcribe from the queue.
			final File fileToTranscribe = theController.getFileToTranscribe() ;
			if( null == fileToTranscribe )
			{
				// Queue is empty.
				// Wait for a job to appear or a stop order to be issued.
				try
				{
					Thread.sleep( 100 ) ;
				}
				catch( Exception theException )
				{
					log.warning( getName() + " Exception: " + theException.toString() ) ;
				}
				continue ;
			}

			// Transcribe this file.
			doTranscribeFile( fileToTranscribe ) ;

			log.info( getName() + " Deleting transcribe input file: " + fileToTranscribe.getAbsolutePath() ) ;
			if( !common.getTestMode() )
			{
				fileToTranscribe.delete() ;
			}
			log.info( getName() + " Completed transcribe on file: " + fileToTranscribe.getAbsolutePath() ) ;
		}
		log.info( getName() + " Thread is shutdown." ) ;
	}

	/**
	 * Run transcribe on a single file. Delete the original file when complete.
	 * @param fileToTranscribe
	 */
	public boolean doTranscribeFile( final File fileToTranscribe )
	{
		assert( fileToTranscribe != null ) ;
		
		log.info( getName() + " Running transcribe on file: " + fileToTranscribe.getAbsolutePath() ) ;
		setWorkInProgress( true ) ;
		
		// The output filename will default, as will the language file.
		// Generate an SRT via whisper ai.
		final String srtFileNameWithPath = fileToTranscribe.getAbsolutePath().replace( ".wav", ".en.srt" ) ;

		File srtFile = new File( srtFileNameWithPath ) ;
		if( !srtFile.exists() )
		{
			// File does not already exist -- create it.
			srtFile = whisper.transcribeToSRT( fileToTranscribe ) ;
		}
		setWorkInProgress( false ) ;

		return ((srtFile != null) && srtFile.exists() ) ;
	}
	
	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}
}
