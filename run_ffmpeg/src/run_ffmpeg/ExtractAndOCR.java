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
	private transient ExtractPGSFromMKVs extractPGSFromMKV = null ; 
	private transient OCRSubtitle ocrSubtitle = null ;

	/// The structure used to pass files that have had their subtitles successfully extracted
	/// to the ocrThread to OCR them.
	private List< File > pipeline_extractedFilesToOCR = null ;

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
		synchronized( pipeline_extractedFilesToOCR )
		{
			retMe = pipeline_extractedFilesToOCR.isEmpty() ;
		}
		return retMe ;
	}

	@Override
	public void run()
	{
		if( extractPGSFromMKV != null )
		{
			// Run the extract method here.
			extractPGSFromMKV.run() ;
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
		List< String > foldersToExtractAndOCR_Local = new ArrayList< String >() ;
		List< String > foldersToExtractAndOCR_ChainA = new ArrayList< String >() ;
		List< String > foldersToExtractAndOCR_ChainB = new ArrayList< String >() ;

		// Will run three threads: local, chain A, and chain B
		// The thinking is that each of the three can operate without interfering with each other
		//  since the primary bottleneck is the drive/network access
		foldersToExtractAndOCR_Local.add( "C:\\Temp\\Battlestar Galactica (2003)\\Season 02" ) ;
		foldersToExtractAndOCR_ChainB.add( "\\\\yoda\\MKV_Archive1\\To Convert" ) ;
		foldersToExtractAndOCR_ChainB.add( "\\\\yoda\\MKV_Archive8\\To Convert" ) ;
		foldersToExtractAndOCR_ChainB.add( "\\\\yoda\\MKV_Archive8\\To Convert - TV Shows" ) ;
		foldersToExtractAndOCR_ChainB.add( "\\\\yoda\\MKV_Archive9\\To Convert" ) ;

//		foldersToExtractAndOCR.addAll( common.addToConvertToEachDrive( common.getAllMKVDrives() ) ) ;
//		foldersToExtractAndOCR.addAll( common.addMoviesAndFoldersToEachDrive( common.getAllMKVDrives() ) ) ;

		// foldersToOCR will hold the sum of all folders to extract
		// This will perform an OCR for each of the folders for any .sup files that
		// reside there when this program starts.
		// The pipline_extractedFilesToOCR will further serve as the mechanism to communicate
		//  between each of the extract objects when a .mkv has been extracted, thus signaling
		//  to the OCR object to initiate an OCR on the corresponding .sup file.
		List< String > foldersToOCR = new ArrayList< String >() ;
		foldersToOCR.addAll( foldersToExtractAndOCR_Local ) ;
		foldersToOCR.addAll( foldersToExtractAndOCR_ChainA ) ;
		foldersToOCR.addAll( foldersToExtractAndOCR_ChainB ) ;

		// Instantiate the OCR object to process the .sup files in the given folder list and
		//  receive new .sup files via the pipeline.
		ocrSubtitle = new OCRSubtitle() ;
		ocrSubtitle.addFoldersToOCR( foldersToOCR ) ;

		// filesToOCR is the mechanism to communicate the successful creation of .sup files
		// from extractPGSFromMKVs to the ocrSubtitle instances.
		// Here I only care about the handle to the instance, not what's in it.
		pipeline_extractedFilesToOCR = ocrSubtitle.getFilesToOCR() ;

		// Create an Extract object for each chain of interest.
		// Inform each object of the pipeline, and tell it the set of folders it
		//  is responsible to extract.
		ExtractPGSFromMKVs extractPGSFromMKV_Local = new ExtractPGSFromMKVs() ;
		extractPGSFromMKV_Local.setTranscodePipeline( pipeline_extractedFilesToOCR ) ;
		extractPGSFromMKV_Local.setDrivesAndFoldersToExtract( foldersToExtractAndOCR_Local ) ;

		ExtractPGSFromMKVs extractPGSFromMKV_ChainA = new ExtractPGSFromMKVs() ;
		extractPGSFromMKV_ChainA.setTranscodePipeline( pipeline_extractedFilesToOCR ) ;
		extractPGSFromMKV_ChainA.setDrivesAndFoldersToExtract( foldersToExtractAndOCR_ChainA ) ;

		ExtractPGSFromMKVs extractPGSFromMKV_ChainB = new ExtractPGSFromMKVs() ;
		extractPGSFromMKV_ChainB.setTranscodePipeline( pipeline_extractedFilesToOCR ) ;
		extractPGSFromMKV_ChainB.setDrivesAndFoldersToExtract( foldersToExtractAndOCR_ChainB ) ;

		// Create a controller thread for the OCR subsystem and one for each Extract object per chain
		ExtractAndOCR ocrThread = new ExtractAndOCR() ;
		ocrThread.setRunOCR( ocrSubtitle ) ;
		
		ExtractAndOCR extractThread_Local = new ExtractAndOCR() ;
		extractThread_Local.setRunExtract( extractPGSFromMKV_Local ) ;

		ExtractAndOCR extractThread_ChainA = new ExtractAndOCR() ;
		extractThread_ChainA.setRunExtract( extractPGSFromMKV_ChainA ) ;

		ExtractAndOCR extractThread_ChainB = new ExtractAndOCR() ;
		extractThread_ChainB.setRunExtract( extractPGSFromMKV_ChainB ) ;

		// Start both
		try
		{
			log.info( "Starting threads." ) ;
			extractThread_Local.start() ;
			extractThread_ChainA.start() ;
			extractThread_ChainB.start() ;
			ocrThread.start() ;
			log.info( "Running threads..." ) ;

			// Keep running the program so long as the stop file does not exist and at
			//  least one subsystem thread is alive.
			while( shouldKeepRunning()
					&& (extractThread_Local.isAlive()
					|| extractThread_ChainA.isAlive()
					|| extractThread_ChainB.isAlive()) )
			{
				Thread.sleep( 100 ) ;
			}

			log.info( "Stopping the threads..." ) ;
			extractPGSFromMKV_Local.stopRunningThread() ;
			extractPGSFromMKV_ChainA.stopRunningThread() ;
			extractPGSFromMKV_ChainB.stopRunningThread() ;
			
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
			ocrSubtitle.stopRunningThread() ;

			log.info( "Joining threads..." ) ; 
			extractThread_Local.join() ;
			extractThread_ChainA.join() ;
			extractThread_ChainB.join() ;
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
		this.extractPGSFromMKV = extractPGSFromMKVs ;
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
