package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

public class OCRSubtitlesWorkerThread extends run_ffmpegWorkerThread
{
	/// Reference back to the controller thread.
	private transient OCRSubtitles theController = null ;

	public OCRSubtitlesWorkerThread( OCRSubtitles theController,
			Logger log,
			Common common )
	{
		super( log, common ) ;
		
		assert( theController != null ) ;

		this.theController = theController ;
	}

//	@Override
//	public boolean hasMoreWork()
//	{
//		// This object operates by asking the producer for work
//		return theController.hasMoreWork() ;
//	}
	
	@Override
	public void run()
	{
		log.info( getName() + " New thread reporting for duty." ) ;
		while( shouldKeepRunning() )
		{
			// Get a file to OCR from the queue.
			final File fileToOCR = theController.getFileToOCR() ;
			if( null == fileToOCR )
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

			// OCR this file.
			doOCRFile( fileToOCR ) ;

			log.info( getName() + " Deleting OCR input file: " + fileToOCR.toString() ) ;
			if( !common.getTestMode() )
			{
				fileToOCR.delete() ;
			}
			log.info( getName() + " Completed OCR on file: " + fileToOCR.toString() ) ;
		}
		log.info( getName() + " Thread is shutdown." ) ;
	}

	/**
	 * Run OCR on a single file. Delete the original file when complete.
	 * @param fileNameToOCR
	 */
	public boolean doOCRFile( final File fileToOCR )
	{
		log.info( getName() + " Running OCR on file: " + fileToOCR.toString() ) ;
		setWorkInProgress( true ) ;
		// Format is:
		// dotnet PgsToSrt.dll --input video1.fr.sup --output video1.fr.srt --tesseractlanguage fra --tesseractdata path_to_language_files
		// The output filename will default, as will the language file.
		// However, will need to include path to the tessdata
		// Gonna try SubtitleEdit for a while.
		ImmutableList.Builder< String > ocrExecuteCommand = new ImmutableList.Builder<String>() ;
		//		ocrExecuteCommand.add( Common.getPathToDotNet(), Common.getPathToPgsToSrtDLL() ) ;
		//		ocrExecuteCommand.add( "--input", fileToOCR.getAbsolutePath() ) ;
		//		ocrExecuteCommand.add( "--tesseractdata", Common.getPathToTessdata() ) ;
		//		ocrExecuteCommand.add( "--tesseractversion", Common.getTesseractVersion() ) ;
		ocrExecuteCommand.add( common.getPathToSubtitleEdit() ) ;
		ocrExecuteCommand.add( "/convert" ) ;
		ocrExecuteCommand.add( fileToOCR.getAbsolutePath() ) ;
		ocrExecuteCommand.add( "subrip" ) ;
		ocrExecuteCommand.add( "/FixCommonErrors" ) ;
		ocrExecuteCommand.add( "/RemoveFormatting" ) ;
		ocrExecuteCommand.add( "/RemoveLineBreaks" ) ;		

		boolean commandSuccess = common.executeCommand( ocrExecuteCommand ) ;
		log.info( getName() + " OCR on file " + fileToOCR.getAbsolutePath() + ": " + commandSuccess ) ;

		final String outputFileName = fileToOCR.getAbsolutePath().replace( ".sup", ".srt" ) ;
		final File outputFile = new File( outputFileName ) ;

		if( !outputFile.exists() )
		{
			log.warning( getName() + " Output file does not exist: " + outputFile.getAbsolutePath() ) ;
			commandSuccess = false ;
		}
		
		if( outputFile.length() < 10 )
		{
			log.warning( getName() + " Output file too small: " + outputFile.getAbsolutePath() ) ;
			commandSuccess = false ;
		}

		if( !commandSuccess )
		{
			log.warning( getName() + " OCR failed; deleting file: " + outputFileName ) ;
			outputFile.delete() ;
		}
		setWorkInProgress( false ) ;
		return commandSuccess ;
	}
	
	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}
}
