package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.mongodb.client.MongoCollection;

/**
 * This class processes OCR requests from the database.
 */
public class WorkflowStageThread_SubtitleOCR extends WorkflowStageThread
{
	/// The createSRTWithOCRCollection is the collection of file names with paths to OCR.
	protected transient MongoCollection< JobRecord_OCRFile > createSRTWithOCRCollection = null ;

	public WorkflowStageThread_SubtitleOCR( final String threadName, Logger log, Common common, MoviesAndShowsMongoDB masMDB )
	{
		super( threadName, log, common, masMDB ) ;

		initObject() ;
	}

	private void initObject()
	{
		createSRTWithOCRCollection = masMDB.getAction_CreateSRTsWithOCRCollection() ;
	}

	/**
	 * Override this method. This method performs the work intended for subclass instances of this class.
	 * @return true if work completed false if no work completed.
	 */
	public boolean doAction()
	{
		final JobRecord_OCRFile ocrJobRecord = createSRTWithOCRCollection.findOneAndDelete( null ) ;
		if( null == ocrJobRecord )
		{
			// No more entries left to process.
			return false ;
		}
		
		final String fileNameWithPathToOCR = ocrJobRecord.getFileNameWithPath() ;
		final File fileToOCR = new File( fileNameWithPathToOCR ) ;

		log.info( getName() + " Running OCR on file: " + fileToOCR.getAbsolutePath() ) ;

		ImmutableList.Builder< String > ocrExecuteCommand = new ImmutableList.Builder<String>() ;
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
		else
		{
			// Command succeeded. Delete the .sup file.
			fileToOCR.delete() ;
		}
		return commandSuccess ;
	}

	@Override
	public String getUpdateString()
	{
		return "Database size: " + createSRTWithOCRCollection.countDocuments() ;
	}
}
