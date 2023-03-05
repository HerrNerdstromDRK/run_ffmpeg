package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

public class run_ffmpeg
{
	private transient Logger log = null ;
	private transient Common common = null ;
	private transient TranscodeCommon transcodeCommon = null ;
	
	/// Note, for some reason this program needs to use the F:\\ nomenclature for representing file paths,
	/// and NOT have a trailing separator
	/// Note also that the paths for the below directories need to be similar for matching to work.
	/// That is, "\\\\yoda\\Backup" is treated different from "X:\Backup"

	/// Directory from which to read the input files to transcode
//	static String mkvInputDirectory = "C:\\Temp\\Game Of Thrones" ;
//	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive9\\To Convert - TV Shows" ;
//	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive7\\To Convert\\Madagascar 3 Europes Most Wanted (2012)" ;
//	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive9\\Movies\\Ip Man 2 (2010)" ;
//	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive9\\To Convert" ;
	static String mkvInputDirectory = "C:\\Users\\Dan\\Desktop\\drive-download-20230211T202934Z-001" ;
//	static String mkvInputDirectory = "E:\\To Convert - TV Shows" ;

	/// Directory to which to move MKV files for storage
	static String mkvFinalDirectory = mkvInputDirectory ;
//	static String mkvFinalDirectory = "C:\\Temp\\The Americans" ;
//	static String mkvFinalDirectory = "\\\\yoda\\MKV_Archive9\\Movies" ;
//	static String mkvFinalDirectory = "\\\\yoda\\MKV_Archive9\\TV Shows" ;
//	static String mkvArchiveDirectory = "\\\\yoda\\Backup\\Ali Backup\\Karate Pictures" ;
//	static String mkvArchiveDirectory = "F:/MKV" ;

	/// Directory to which to place transcoded MP4 files during transcoding
//	static String mp4OutputDirectory = mkvInputDirectory ;
//	static String mp4OutputDirectory = "c:\\Temp" ;
//	static String mp4OutputDirectory = "C:\\Users\\Dan\\Desktop" ;
	static String mp4OutputDirectory = "D:\\Temp" ;
//	static String mp4OutputDirectory = "\\\\yoda\\Videos\\Videos\\TV Shows\\Rick and Morty" ;
//	static String mp4OutputDirectory = "\\\\yoda\\Backup\\Ali Backup\\Transcoded" ;

	/// Directory to which to move mp4 files once complete
//	static String mp4FinalDirectory = mp4OutputDirectory ;
//	static String mp4FinalDirectory = mkvInputDirectory ;
//	static String mp4FinalDirectory = "\\\\yoda\\MKV_Archive8\\To Convert - TV Shows\\Band Of Brothers\\Season 01" ;
//	static String mp4FinalDirectory = "\\\\yoda\\MP4_4\\Movies" ;
	static String mp4FinalDirectory = "\\\\yoda\\MP4\\Other Videos" ;
//	static String mp4FinalDirectory = "\\\\yoda\\MP4_4\\TV Shows" ;

	/// Set to true to move the mp4/mkv/srt files to the destination
	static boolean doMoveMP4 = false ;
	static boolean doMoveMKV = false ;
	static boolean doMoveSRT = false ;
	
	/// Set to true if this application should overwrite existing MP4 files; false otherwise
	static boolean overwriteMP4s = false ;

	/// Set to true to host two move threads that move MKV and MP4 files in parallel
	static boolean moveMKVAndMP4InParallel = true ;

	/// The workerThread will perform all of the operations that can be performed in the background, like
	/// moving files. This thread is NOT intended to transcode
	private ExecThread[] workerThreads = null ;

	/// Indices to the workerThreads array. Note that these will both be zero if moveMKVAndMP4InParallel is false
	private int mkvMoveThreadIndex = 0 ;
	private int mp4MoveThreadIndex = 1 ;

	/// Identify the pattern for transcode
	enum transcodeOrderingType
	{
		transcodeByDirectory,
		transcodeSmallToLarge,
		transcodeLargeToSmall
	} ;
	static transcodeOrderingType transcodeOrder = transcodeOrderingType.transcodeSmallToLarge ;

	/// As some of the test runs generate enormous amounts of text output, capture it all in a log file, as well as in the console
	/// Setup the logging subsystem
	private static final String logFileName = "log.txt" ;

	/// In most cases, the included algorithm is smart enough to extract the metadata title from the
	/// name of the file. But in some cases, such as with home movies, the filenames are inconsistent. In those
	/// cases, just use the filename as the title.
	static boolean useFileNameAsTitle = true ;

	/// When a file named stopFileName exists (including directory), then stop the application at the next
	/// possible time (after the current transcode and move).
	static final String stopFileName = "C:\\Temp\\stop.txt" ;

	private run_ffmpeg()
	{
		log = Common.setupLogger( logFileName, "run_ffmpeg" ) ;
		common = new Common( log ) ;
		transcodeCommon = new TranscodeCommon( log,
				common,
				mkvInputDirectory,
				mkvFinalDirectory,
				mp4OutputDirectory,
				mp4FinalDirectory ) ;
	}
	
    public static void main(String[] args)
	{
    	run_ffmpeg runMe = new run_ffmpeg() ;
    	runMe.run() ;
	}
    
    public void run()
    {
    	outputConfigurationHeader() ;
				
		if( moveMKVAndMP4InParallel )
		{
			workerThreads = new ExecThread[ 2 ] ;
		}
		else
		{
			workerThreads = new ExecThread[ 1 ] ;
			mp4MoveThreadIndex = 0 ;
		}

		// Instantiate and start the worker threads
		for( int workerThreadsIndex = 0 ; workerThreadsIndex < workerThreads.length ; ++workerThreadsIndex )
		{
			String execThreadName = "MP4 Move Thread" ;
			if( (workerThreads.length > 1) && (0 == workerThreadsIndex ) )
			{
				// Two threads, and this is the first
				execThreadName = "MKV Move Thread" ;
			}
			workerThreads[ workerThreadsIndex ] = new ExecThread( execThreadName, common, log ) ;
			workerThreads[ workerThreadsIndex ].start() ;
		}
		
		// First, survey the input directory for files to transcode, and build
		// a TranscodeFile object for each.
		List< TranscodeFile > filesToTranscode = transcodeCommon.surveyInputDirectoryAndBuildTranscodeFiles(
				mkvInputDirectory, transcodeCommon.getTranscodeExtensions() ) ;
		List< TranscodeFile > filesToTranscodeInOrder = orderFilesToTranscode( filesToTranscode ) ;
		for( TranscodeFile fileToTranscode : filesToTranscodeInOrder )
		{
			log.fine( fileToTranscode.toString() ) ;
		}
		
	  	final long startTime = System.nanoTime() ;

	  	// Perform the core work of this application: for each input file, create the appropriate directories,
	  	// transcode the file, and move the input and output files (if necessary).
		for( TranscodeFile fileToTranscode : filesToTranscodeInOrder )
		{
			if( common.shouldStopExecution( stopFileName ) )
			{
				break ;
			}
			
			if( (fileToTranscode.isTranscodeInProgress() && !overwriteMP4s)
					|| (fileToTranscode.isTranscodeComplete() && !overwriteMP4s) )
			{
				// The mp4 file already exists and we are not overwriting it.
				// Skip this loop and move to the next file.
				log.info( "Input file (" + fileToTranscode.getMkvFileName() + ") already has an mp4 equivalent. Skipping transcode." ) ;
				continue ;
			}
			
			// Probe the file's streams
			FFmpegProbeResult ffmpegProbeResult = common.ffprobeFile( fileToTranscode, log ) ;
			if( null == ffmpegProbeResult )
			{
				log.info( "transcodeFile(" + fileToTranscode.getMkvFileName() + ")> null ffmpegProbeResult" ) ;
				continue ;
			}

			fileToTranscode.processFFmpegProbeResult( ffmpegProbeResult ) ;
			fileToTranscode.makeDirectories() ;
			fileToTranscode.setTranscodeInProgress();
			
			boolean transcodeSucceeded = transcodeCommon.transcodeFile( fileToTranscode ) ;
			if( transcodeSucceeded )
			{
				fileToTranscode.setTranscodeComplete();
		    	// Move the MKV file to its final directory
		    	// NOTE: I could easily check the move booleans here and prevent this
		    	// code from executing. However, I have chosen to use those booleans deeper
		    	// in the call tree for testing purposes -- when test mode is enabled, I
		    	// want the code to be executed and the worker threads to output that they
		    	// are working.
		    	if( fileToTranscode.getMKVFileShouldMove() )
		    	{
		    		// moveFile respects the testMode variable
		    		moveFile( fileToTranscode.getMKVFileNameWithPath(), fileToTranscode.getMkvFinalFileNameWithPath() ) ;
		    		if( doMoveSRT )
		    		{
			    		for( Iterator< File > fileIterator = fileToTranscode.getSRTFileListIterator() ; fileIterator.hasNext() ; )
			    		{
			    			File srtFile = fileIterator.next() ;
			    			if( !srtFile.getAbsolutePath().equalsIgnoreCase( fileToTranscode.getMkvFinalDirectory() ) )
			    			{
			    				moveFile( srtFile.getAbsolutePath(), fileToTranscode.getMkvFinalDirectory() + srtFile.getName() ) ;
			    			}
			    		}
		    		}
		    	}
		    	if( fileToTranscode.getMP4FileShouldMove() )
		    	{
		    		moveFile( fileToTranscode.getMp4OutputFileNameWithPath(), fileToTranscode.getMP4FinalOutputFileNameWithPath() ) ;
		    	}
			}
			else
			{
				// Transcode failed
				fileToTranscode.unSetTranscodeInProgress();
			}
			
			// Free any unused memory or handles
			System.gc() ;
		}
		log.info( "Shutting down..." ) ;
		try
		{
			log.info( "Waiting for worker threads to finish" ) ;
			
			// After the last file is transcoded, the workerThread(s) will perform two move functions
			// Wait for the workerThreads work queue to become empty
			for (ExecThread workerThread : workerThreads)
			{
				while( workerThread.hasMoreWork() )
				{
					// Let the workerThread finish its business
					Thread.sleep( 100 ) ;
				}

				// Gracefully shutdown the worker thread
		    	workerThread.stopRunning() ;
				workerThread.join() ;
				log.info( "Successfully shut down workerThread: " + workerThread.toString() ) ;
			}
		}
		catch( Exception theException )
		{
			log.info( "Exception joining with workerThread: " + theException ) ;
		}

		final long endTime = System.nanoTime() ;

		log.info( common.makeElapsedTimeString( startTime, endTime ) ) ;
		log.info( "Shutdown complete." ) ;
	} // main()

	public void buildDirectories( final TranscodeFile inputFile )
	{
		common.makeDirectory( inputFile.getMkvFinalDirectory() ) ;
		common.makeDirectory( inputFile.getMp4OutputDirectory() ) ;
		common.makeDirectory( inputFile.getMp4FinalDirectory() ) ;
	}

    public void moveFile( final String sourceFileName, final String destinationFileName )
	{
    	log.info( "Moving file " + sourceFileName + " to " + destinationFileName ) ;
    	
    	// Set the move variable based on the type of file being moved
		boolean doMove = false ;
		if( sourceFileName.endsWith( ".mkv" ) ) doMove = doMoveMKV ;
		else if( sourceFileName.endsWith( ".mp4" ) ) doMove = doMoveMP4 ;
		else if( sourceFileName.endsWith( ".srt" ) ) doMove = doMoveSRT ;
		else
		{
			log.info( "Unable to find move boolean for input file: " + sourceFileName ) ;
		}

		// Execute the move if the destination file doesn't already exits
		// and if we have determined (from above) this type of file should be moved
		// Note that the MoveFileThreadAction respects the testMode variable
		if( !sourceFileName.equalsIgnoreCase( destinationFileName ) && doMove )
		{
	    	MoveFileThreadAction theMoveFileThreadAction =
	    			new MoveFileThreadAction( sourceFileName, destinationFileName, common, log ) ;
	
	    	int workerThreadsIndex = mkvMoveThreadIndex ;
	    	if( sourceFileName.contains( ".mp4" ) )
	    	{
	    		workerThreadsIndex = mp4MoveThreadIndex ;
	    	}
	
	    	// Move the input file to its archive location
	    	workerThreads[ workerThreadsIndex ].addWork( theMoveFileThreadAction ) ;
	    }
	}

	public void outputConfigurationHeader()
	{
		log.info( "" ) ;
		log.info( "*** Configuration ***" ) ;
		log.info( "*** mkvInputDirectory: " + mkvInputDirectory + " ***" ) ;
		log.info( "*** mkvFinalDirectory: " + mkvFinalDirectory + " ***" ) ;
		log.info( "*** mp4OutputDirectory: " + mp4OutputDirectory + " ***" ) ;
    	log.info( "*** mp4FinalDirectory: " + mp4FinalDirectory + " ***" ) ;
    	log.info( "*** testMode: " + common.getTestMode() + " ***" ) ;
    	log.info( "*** doMoveMP4: " + doMoveMP4 + " ***" ) ;
    	log.info( "*** doMoveMKV: " + doMoveMKV + " ***" ) ;
    	log.info( "*** doMoveSRT: " + doMoveSRT + " ***" ) ;
    	log.info( "*** doTranscodeVideo: " + transcodeCommon.isDoTranscodeVideo() + " ***" ) ;
    	log.info( "*** isWindows: " + common.getIsWindows() + " ***" ) ;
    	log.info( "*** pathToFFMPEG: " + Common.getPathToFFmpeg() + " ***" ) ;
    	log.info( "*** pathToFFPROBE: " + Common.getPathToFFprobe() + " ***" ) ;
    	String transcodeOrderString = "transcodeByDirectory" ;
    	if( transcodeOrder == transcodeOrderingType.transcodeSmallToLarge )
    	{
    		transcodeOrderString = "transcodeSmallToLarge" ;
    	}
    	else if( transcodeOrder == transcodeOrderingType.transcodeLargeToSmall )
    	{
    		transcodeOrderString = "transcodeLargeToSmall" ;
    	}
    	log.info( "*** transcodeOrder: " + transcodeOrderString + " ***" ) ;
    	log.info( "*** logFileName: " + logFileName + " ***" ) ;
    	log.info( "*** useFileNameAsTitle: " + useFileNameAsTitle + " ***" ) ;
    	log.info( "*** stopFileName: " + stopFileName + " ***" ) ;
    	log.info( "*** transcodeExtensions: " + common.toString( transcodeCommon.getTranscodeExtensions() ) + " ***" ) ;
    	log.info( "*** moveMKVAndMP4InParallel: " + moveMKVAndMP4InParallel + " ***" ) ;
    	log.info( "*** forcedSubTitleFileNameContains: " + transcodeCommon.getForcedSubTitleFileNameContains() + " ***" ) ;
    	log.info( "*** audioTranscodeLibrary: " + transcodeCommon.getAudioTranscodeLibrary() + " ***" ) ;
    	log.info( "*** addAudioStereoStream: " + transcodeCommon.isAddAudioStereoStream() + " ***" ) ;
    	String audioStreamTranscodeOptionsString = "audioStreamAll" ;
    	if( transcodeCommon.isAudioStreamOptionAudioStreamEnglishOnly() )
    	{
    		audioStreamTranscodeOptionsString = "audioStreamEnglishOnly" ;
    	}
    	else if( transcodeCommon.isAudioStreamOptionAudioStreamPrimaryPlusEnglish() )
    	{
    		audioStreamTranscodeOptionsString = "audioStreamPrimaryPlusEnglish" ;
    	}
    	else if( transcodeCommon.isAudioStreamOptionAudioStreamAudioStreamsByName() )
    	{
    		audioStreamTranscodeOptionsString = "audioStreamsByName: " + common.toString( transcodeCommon.getAudioStreamsByNameArray() ) ;
    	}
    	log.info( "*** audioStreamTranscodeOptions: " + audioStreamTranscodeOptionsString + " ***" ) ;
    	log.info( "" ) ;
   	}

	public List< TranscodeFile > orderFilesToTranscode( final List< TranscodeFile > theFiles )
	{
		SortedMap< Long, TranscodeFile > filesBySizeMap = null ;
		switch( transcodeOrder )
		{
		case transcodeByDirectory:
			// Default ordering is by directory
			break;
		case transcodeSmallToLarge:
			filesBySizeMap = new TreeMap< Long, TranscodeFile >() ;
			break ;
		case transcodeLargeToSmall:
			filesBySizeMap = new TreeMap< Long, TranscodeFile >( Collections.reverseOrder() ) ;
			break ;
		}
	
		List< TranscodeFile > filesByOrder = theFiles ;
		if( filesBySizeMap != null )
		{
			for( TranscodeFile theFile : theFiles )
			{
				filesBySizeMap.put( Long.valueOf( theFile.getInputFileSize() ), theFile ) ;
			}
			filesByOrder = new ArrayList< TranscodeFile >( filesBySizeMap.values() ) ;
		}
		return filesByOrder ;
	}

} // run_ffmpeg
