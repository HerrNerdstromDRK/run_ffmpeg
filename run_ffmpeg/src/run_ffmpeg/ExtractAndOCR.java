package run_ffmpeg;

import java.util.logging.Logger;

/**
 * The purpose of this class is to run both the extract subtitles and OCR workflows
 * simultaneously. The thinking is that the first uses primarily network and HDD,
 * and the second uses primarily CPU, so they should be able to run in parallel.
 * @author Dan
 */
public class ExtractAndOCR extends Thread
{
	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_extract_and_ocr.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_extract_and_ocr.txt" ;
	
	/// Set this variable to true to execute the ExtractPGS controller thread.
	/// False means run the OCR controller thread.
	private boolean runExtract = false ;
	
	/// Set to true to keep the instances running, set to false otherwise.
	/// This is meant to provide a programmatic way of shutting down all of the threads.
	private boolean keepThreadRunning = true ;
	
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
	
	public void runThreads()
	{
		// ExtractPGS spawns two additional threads as workers and keeps the owning
		// thread as the controller.
		// Need to spawn the controller thread here in a separate thread.
		ExtractAndOCR extractThread = new ExtractAndOCR() ;
		extractThread.setRunExtract( true ) ;
		
		ExtractAndOCR OCRThread = new ExtractAndOCR() ;
		
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
			// Stop the threads.
			log.info( "Stopping the threads..." ) ;
			extractThread.stopRunningThread() ;
			OCRThread.stopRunningThread() ;
			
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
	
	@Override
	public void run()
	{
		if( isRunExtract() )
		{
			// Run the extract method here.
			ExtractPGSFromMKVs extractPGSFromMKVs = new ExtractPGSFromMKVs() ;
			extractPGSFromMKVs.runThreads() ;
		}
		else
		{
			// Run the OCR.
			OCRSubtitle ocrSubtitle = new OCRSubtitle() ;
			ocrSubtitle.runThreads() ;
		}
	}

	public boolean isRunExtract() {
		return runExtract;
	}

	public void setRunExtract(boolean runExtract) {
		this.runExtract = runExtract;
	}
	
	public boolean isKeepThreadRunning() {
		return keepThreadRunning;
	}

	public void setKeepThreadRunning(boolean keepThreadRunning) {
		this.keepThreadRunning = keepThreadRunning;
	}

	public boolean shouldKeepRunning()
	{
		return (!common.shouldStopExecution( getStopFileName() ) && isKeepThreadRunning()) ;
	}

	public void stopRunningThread()
	{
		setKeepThreadRunning( false ) ;
	}

	public String getStopFileName() {
		return stopFileName;
	}
	
}
