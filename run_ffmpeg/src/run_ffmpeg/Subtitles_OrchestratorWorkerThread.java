package run_ffmpeg;

import java.util.logging.Logger;

/**
 * This worker class encapsulates either an extract/ocr/transcribe worker object and serves as proxy
 *  for the run_ffmpegControllerThreadTemplate to execute the subordinate threads of each
 *  of the contained objects.
 */
public class Subtitles_OrchestratorWorkerThread extends run_ffmpegWorkerThread
{
	private Subtitles_Orchestrator theController = null ;
	
	/// Exactly one of these will be non-null and thus represent the function of this object
	private Subtitles_Extract extractSubtitles = null ;
	private Subtitles_OCR ocrSubtitles = null ;
	private Subtitles_Transcribe transcribeSubtitles = null ;
	
	public Subtitles_OrchestratorWorkerThread( Logger log,
			Common common,
			Subtitles_Orchestrator theController,
			Subtitles_Extract extractSubtitles,
			Subtitles_OCR ocrSubtitles,
			Subtitles_Transcribe transcribeSubtitles )
	{
		super( log, common ) ;
		assert( theController != null ) ;
		
		this.theController = theController ;
		this.extractSubtitles = extractSubtitles ;
		this.ocrSubtitles = ocrSubtitles ;
		this.transcribeSubtitles = transcribeSubtitles ;
	}

	@Override
	public void run()
	{
		// Calling execute on each object will initiate the entire chain of methods that a controller
		// thread normally conducts. The only difference is that this object (and its controller object)
		// tie the two together to form a pipeline.
		// Note: This code explicitly does *not* call Init() since that method would set the folders
		// to extract/OCR/transcribe to whatever is already there -- Subtitles_Orchestrator has already configured
		// each of the subordinate objects appropriately.
		// Calling Execute() here is effectively a blocking method.
		if( extractSubtitles != null )
		{
			extractSubtitles.Execute() ;
			log.info( getName() + " Shutting down." ) ;
		}
		else if( ocrSubtitles != null )
		{
			ocrSubtitles.Execute() ;
			log.info( getName() + " Shutting down." ) ;
		}
		else if( transcribeSubtitles != null )
		{
			transcribeSubtitles.Execute() ;
			log.info( getName() + " Shutting down." ) ;
		}
	}

	protected boolean atLeastOneThreadIsAlive()
	{
		if( extractSubtitles != null )
		{
			return extractSubtitles.atLeastOneThreadIsAlive() ;
		}
		else if( ocrSubtitles != null )
		{
			return ocrSubtitles.atLeastOneThreadIsAlive() ;
		}
		else if( transcribeSubtitles != null )
		{
			return transcribeSubtitles.atLeastOneThreadIsAlive() ;
		}
		return false ;
	}
	
	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}
}
