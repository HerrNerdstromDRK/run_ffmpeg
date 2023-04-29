package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * The purpose of this class is to run both the extract subtitles and OCR workflows
 * simultaneously. The thinking is that the first uses primarily network and HDD,
 * and the second uses primarily CPU, so they should be able to run in parallel.
 * @author Dan
 */
public class ExtractAndOCR extends Thread
{
	/// The two instances, one of extractpgs and the other of ocrsubtitle, for
	/// the worker threads to execute. Each thread will execute one of those
	/// two types. The intent here is for the ExtractAndOCR controller thread
	/// to have a means to issue a stop work for each of the subordinate controller
	/// threads.
	private transient ExtractPGSFromMKVs extractPGSFromMKVs = null ; 
	private transient OCRSubtitle ocrSubtitle = null ;
	
	/// The structure used to pass files that have had their subtitles successfully extracted
	/// to the ocrThread to OCR them.
	private List< File > filesToOCR = null ;
	
	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_extract_and_ocr.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_extract_and_ocr.txt" ;
	
	public ExtractAndOCR()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}
	
	public static void main( String[] args )
	{
		ExtractAndOCR controllerThread = new ExtractAndOCR() ;
		controllerThread.runThreads() ;
	}
	
	public String getStopFileName()
	{
		return stopFileName;
	}

	/**
	 * Return true if the file pipeline between the Extract and OCR threads is empty, false otherwise.
	 * This method synchronizes on the pipeline.
	 * @return
	 */
	public boolean pipelineIsEmpty()
	{
		boolean retMe = false ;
		synchronized( filesToOCR )
		{
			retMe = filesToOCR.isEmpty() ;
		}
		return retMe ;
	}
	
	@Override
	public void run()
	{
		if( extractPGSFromMKVs != null )
		{
			// Run the extract method here.
			extractPGSFromMKVs.runThreads() ;
		}
		else
		{
			// Run the OCR.
			ocrSubtitle.runThreads() ;
		}
	}

	public void runThreads()
	{
		common.setTestMode( false ) ;
		
		// Identify the folders to extract and OCR.
		List< String > localFoldersToExtract = new ArrayList< String >() ;
		localFoldersToExtract.add( "C:\\Temp" ) ;
		
		// ExtractPGS spawns two additional threads as workers and keeps the owning
		// thread as the controller.
		ocrSubtitle = new OCRSubtitle() ;
		ocrSubtitle.addFoldersToOCR( localFoldersToExtract ) ;
		
		// filesToOCR is the mechanism to communicate the successful creation of .sup files
		// from extractPGSFromMKVs to the ocrSubtitle instances.
		// Here I only care about the handle to the instance, not what's in it.
		filesToOCR = ocrSubtitle.getFilesToOCR() ;
		
		extractPGSFromMKVs = new ExtractPGSFromMKVs() ;
		extractPGSFromMKVs.setTranscodePipeline( filesToOCR ) ;

		extractPGSFromMKVs.setDrivesAndFoldersToExtract( localFoldersToExtract ) ;
//		extractPGSFromMKVs.setDrivesAndFoldersToExtract( common.addToConvertToEachDrive( common.getAllMKVDrives() ) ) ;
		
		ExtractAndOCR extractThread = new ExtractAndOCR() ;
		extractThread.setRunExtract( extractPGSFromMKVs ) ;
		
		ExtractAndOCR ocrThread = new ExtractAndOCR() ;
		ocrThread.setRunOCR( ocrSubtitle ) ;
		
		// Start both
		try
		{
			log.info( "Starting threads." ) ;
			extractThread.start() ;
			ocrThread.start() ;
			log.info( "Running threads..." ) ;
			
			while( shouldKeepRunning()
					&& extractThread.isAlive() )
			{
				Thread.sleep( 100 ) ;
			}
			
			// Done extracting subtitles. However, the queue of files to OCR may
			// not be empty.
			// Wait for the OCR queue to be empty.
			log.info( "Waiting for OCR queue to complete." ) ;
			while( !pipelineIsEmpty() )
			{
				Thread.sleep( 100 ) ;
			}

			// Post-condition: Either the stop file now exists, or the extract thread has completed
			//  and the pipeline queue is empty.
			log.info( "Stopping the threads..." ) ;
			extractPGSFromMKVs.stopRunningThread() ;

			// This will still allow the last OCR jobs to complete.
			ocrSubtitle.stopRunningThread() ;
			
			log.info( "Joining threads..." ) ; 
			extractThread.join() ;
			ocrThread.join() ;
		}
		catch( Exception theException )
		{
			log.info( "Exception: " + theException.toString() ) ;
		}
		log.info( "Complete." ) ;
	}
	
	private void setRunExtract( ExtractPGSFromMKVs extractPGSFromMKVs )
	{
		this.extractPGSFromMKVs = extractPGSFromMKVs ;
	}

	private void setRunOCR( OCRSubtitle ocrSubtitle )
	{
		this.ocrSubtitle = ocrSubtitle ;
	}

	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( getStopFileName() ) ;
	}
	
}
