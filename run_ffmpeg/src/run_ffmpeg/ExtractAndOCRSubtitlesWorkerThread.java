package run_ffmpeg;

import java.util.logging.Logger;

/**
 * This worker class encapsulates either an extract or ocr object and serves as proxy
 *  for the run_ffmpegControllerThreadTemplate to execute the subordinate threads of each
 *  of the contained objects.
 */
public class ExtractAndOCRSubtitlesWorkerThread extends run_ffmpegWorkerThread
{
	private ExtractAndOCRSubtitles theController = null ;
	
	/// Exactly one of these will be non-null and thus represent the function of this object
	private ExtractSubtitles extractSubtitles = null ;
	private OCRSubtitles ocrSubtitles = null ;
	
	public ExtractAndOCRSubtitlesWorkerThread( Logger log,
			Common common,
			ExtractAndOCRSubtitles theController,
			ExtractSubtitles extractSubtitles,
			OCRSubtitles ocrSubtitles )
	{
		super( log, common ) ;
		assert( theController != null ) ;
		
		this.theController = theController ;
		this.extractSubtitles = extractSubtitles ;
		this.ocrSubtitles = ocrSubtitles ;
	}

	@Override
	public void run()
	{
		// Calling execute on each object will initiate the entire chain of methods that a controller
		// thread normally conducts. The only difference is that this object (and its controller object)
		// tie the two together to form a pipeline.
		// Note: This code explicitly does *not* call Init() since that method would set the folders
		// to extract/OCR to whatever is already there -- ExtractAndOCRSubtitles has already configured
		// each of the subordinate objects appropriately.
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
		return false ;
	}
	
	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}
}
