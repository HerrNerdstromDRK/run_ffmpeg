package run_ffmpeg;

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
		// ExtractPGS spawns two additional threads as workers and keeps the owning
		// thread as the controller.
		ExtractAndOCR extractThread = new ExtractAndOCR() ;
		extractPGSFromMKVs = new ExtractPGSFromMKVs() ;
		List< String > localFoldersToExtract = new ArrayList< String >() ;
		localFoldersToExtract.add( "C:\\Temp" ) ;
		extractPGSFromMKVs.setDrivesAndFoldersToExtract( localFoldersToExtract ) ;
//		extractPGSFromMKVs.setDrivesAndFoldersToExtract( common.addToConvertToEachDrive( common.getAllMKVDrives() ) ) ;
		extractThread.setRunExtract( extractPGSFromMKVs ) ;
		
		ExtractAndOCR OCRThread = new ExtractAndOCR() ;
		ocrSubtitle = new OCRSubtitle() ;
		OCRThread.setRunOCR( ocrSubtitle ) ;
		
		// Start both
		try
		{
			log.info( "Starting threads." ) ;
			extractThread.start() ;
			OCRThread.start() ;
			log.info( "Running threads..." ) ;
			
			while( shouldKeepRunning()
					&& (extractThread.isAlive()
					|| OCRThread.isAlive()) )
			{
				Thread.sleep( 100 ) ;
			}
			// Post-condition: Either the stop file now exists, or both threads have stopped.
			// Stop the threads in the event that the stop file now exists.
			log.info( "Stopping the threads..." ) ;
			extractPGSFromMKVs.stopRunningThread() ;
			ocrSubtitle.stopRunningThread() ;
			
			log.info( "Joining threads..." ) ; 
			extractThread.join() ;
			OCRThread.join() ;
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
