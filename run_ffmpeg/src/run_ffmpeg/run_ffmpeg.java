package run_ffmpeg;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.google.common.collect.ImmutableList;

public class run_ffmpeg
{
	/// Class-wide NumberFormat for ease of use in reporting data statistics
	static NumberFormat numFormat = NumberFormat.getInstance( new Locale( "en", "US" ) ) ;

	/// Note, for some reason this program needs to use the F:\\ nomenclature for representing file paths,
	/// and NOT have a trailing separator
	/// Note also that the paths for the below directories need to be similar for matching to work.
	/// That is, "\\\\yoda\\Backup" is treated different from "X:\Backup"

	/// Directory from which to read the input files to transcode
//	static String mkvInputDirectory = "C:\\Temp\\Archer" ;
//	static String mkvInputDirectory = "\\\\yoda\\Backup\\Ali Backup\\Karate Pictures" ;
//	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive8\\To Convert - TV Shows\\Band of Brothers" ;
	static String mkvInputDirectory = "\\\\yoda\\Backup\\To Convert - TV Shows\\Weeds" ;
//	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive7\\To Convert\\Madagascar 3 Europes Most Wanted (2012)" ;
//	static String mkvInputDirectory = "C:\\Users\\Dan\\Desktop\\ConvertMe" ;
//	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive6\\To Convert\\Veronica Mars (2014)" ;
//	static String mkvInputDirectory = "\\\\yoda\\Videos\\Videos\\Other Videos" ;
//	static String mkvInputDirectory = "F:/Movies/Brooklyn Nine-Nine" ;

	/// Directory to which to move MKV files for storage
//	static String mkvFinalDirectory = mkvInputDirectory ;
//	static String mkvFinalDirectory = "C:\\Temp\\The Americans" ;
//	static String mkvFinalDirectory = "\\\\yoda\\MKV_Archive8\\To Convert - TV Shows\\Band Of Brothers\\Season 01" ;
//	static String mkvFinalDirectory = "\\\\yoda\\MKV_Archive8\\Movies" ;
	static String mkvFinalDirectory = "\\\\yoda\\MKV_Archive9\\TV Shows" ;
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
//	static String mp4FinalDirectory = "\\\\yoda\\MKV_Archive8\\To Convert - TV Shows\\Band Of Brothers\\Season 01" ;
//	static String mp4FinalDirectory = "\\\\yoda\\MP4_3\\Movies" ;
//	static String mp4FinalDirectory = "\\\\yoda\\MP4\\Other Videos" ;
	static String mp4FinalDirectory = "\\\\yoda\\MP4_4\\TV Shows" ;

	/// Set testMode to true to make execCommand() only output to the console, but not execute the command
	/// Note that testMode supersedes doMove
	static boolean testMode = false ;

	/// Set to true to move the mp4/mkv/srt files to the destination
	static boolean doMoveMP4 = true ;
	static boolean doMoveMKV = true ;
	static boolean doMoveSRT = true ;
	
	/// Determines the path separator
	static boolean isWindows = true ;
	
	/// Set to true if this application should overwrite existing MP4 files; false otherwise
	static boolean overwriteMP4s = false ;

	/// Set to true to enable de-interlacing
	static boolean deInterlaceInput = true ;
	
	/// Separator to use to demarc directories
	static public String pathSeparator = "\\" ;
	
	/// Paths to ffmpeg and ffprobe
	static final String pathToFFMPEG = "D:\\Program Files\\ffmpeg\\bin\\ffmpeg" ;
	static final String pathToFFPROBE = "D:\\Program Files\\ffmpeg\\bin\\ffprobe" ;

	// Include the options for transcoding
	// The standard is that the options strings include no leading or trailing spaces
	// -v yadif=1: de-interlace
	// -copyts: Copies the timestamps into the output file for use by subtitles
	// -c:s copy: Copy the subtitle stream from MKV to MP4. Deleted for now until I can figure out a solution that
	//   supports PGS
	//   - -c:s mov_text
	// -vcodec libx264: Use H.264 codec for otuput
	// -crf 17: Set quality; anything better than 17 will not be noticeable
	// -map 0: Copy all streams, including subtitles
	// -movflags +faststart: Include indexing so it's easy to move anywhere in the file during playback
	static ImmutableList.Builder< String > transcodeOptions = new ImmutableList.Builder<String>() ;

	/// Set to true to host two move threads that move MKV and MP4 files in parallel
	static boolean moveMKVAndMP4InParallel = true ;

	/// The workerThread will perform all of the operations that can be performed in the background, like
	/// moving files. This thread is NOT intended to transcode
	static ExecThread[] workerThreads = null ;

	/// Indices to the workerThreads array. Note that these will both be zero if moveMKVAndMP4InParallel is false
	static int mkvMoveThreadIndex = 0 ;
	static int mp4MoveThreadIndex = 1 ;

	/// Identify the pattern for transcode
	enum transcodeOrdering
	{
		transcodeByDirectory,
		transcodeSmallToLarge,
		transcodeLargeToSmall
	} ;
	static transcodeOrdering transcodeOrder = transcodeOrdering.transcodeSmallToLarge ;

	/// As some of the test runs generate enormous amounts of text output, capture it all in a log file, as well as in the console
	static BufferedWriter logWriter = null ;
	static final String logWriterFileName = "log.txt" ;

	/// In most cases, the included algorithm is smart enough to extract the metadata title from the
	/// name of the file. But in some cases, such as with home movies, the filenames are inconsistent. In those
	/// cases, just use the filename as the title.
	static boolean useFileNameAsTitle = false ;

	/// When a file named stopFileName exists (including directory), then stop the application at the next
	/// possible time (after the current transcode and move).
	static final String stopFileName = "C:\\Temp\\stop.txt" ;

	/// Extensions to transcode to mp4
	static final String[] transcodeExtensions = { ".mkv", ".MOV", ".mov", ".wmv" } ;

    public static void main(String[] args)
	{
		numFormat.setMaximumFractionDigits( 2 ) ;
		openLogFile() ;
		
		// Set the basic transcode options
		if( deInterlaceInput )	transcodeOptions.add( "-vf", "yadif=1" ) ;
		transcodeOptions.add( "-vcodec", "libx264" ) ;
		transcodeOptions.add( "-crf", "17" ) ;
		transcodeOptions.add( "-movflags", "+faststart" ) ;
		
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
			workerThreads[ workerThreadsIndex ] = new ExecThread() ;
			workerThreads[ workerThreadsIndex ].start() ;
		}
		
		// First, survey the input directory for files to transcode, and build
		// a TranscodeFile object for each.
		List< TranscodeFile > filesToTranscode = surveyInputDirectoryAndBuildTranscodeFiles( mkvInputDirectory ) ;
		List< TranscodeFile > filesToTranscodeInOrder = orderFilesToTranscode( filesToTranscode ) ;
		out( filesToTranscodeInOrder ) ;
		
	  	final long startTime = System.nanoTime() ;

	  	// Perform the core work of this application: for each input file, create the appropriate directories,
	  	// transcode the file, and move the input and output files (if necessary).
		for( TranscodeFile fileToTranscode : filesToTranscodeInOrder )
		{
			if( (fileToTranscode.isTranscodeInProgress() && !overwriteMP4s)
					|| (fileToTranscode.isTranscodeComplete() && !overwriteMP4s) )
			{
				// The mp4 file already exists and we are not overwriting it.
				// Skip this loop and move to the next file.
				out( "main> Input file (" + fileToTranscode.getMkvFileName() + ") already has an mp4 equivalent. Skipping transcode." ) ;
				continue ;
			}
			
			// Probe the file's streams
			FFmpegProbeResult ffmpegProbeResult = ExtractPGSFromMKVs.ffprobeFile( fileToTranscode ) ;
			if( null == ffmpegProbeResult )
			{
				out( "transcodeFile(" + fileToTranscode.getMkvFileName() + ")> null ffmpegProbeResult" ) ;
				continue ;
			}
			
			fileToTranscode.makeDirectories() ;
			fileToTranscode.setTranscodeInProgress();
			transcodeFile( fileToTranscode, ffmpegProbeResult ) ;
			fileToTranscode.setTranscodeComplete();

			if( stopExecution() )
			{
				break ;
			}
			
			// Free any unused memory or handles
			System.gc() ;
		}
		out( "main> Shutting down..." ) ;
		try
		{
			out( "main> Waiting for worker threads to finish" ) ;
			
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
			}
		}
		catch( Exception theException )
		{
			out( "main> Exception joining with workerThread: " + theException ) ;
		}

		final long endTime = System.nanoTime() ;
		final double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;

    	out( "main> Total elapsed time: "
    			+ numFormat.format( timeElapsedInSeconds )
    			+ " seconds, "
    			+ numFormat.format( timeElapsedInSeconds / 60.0 )
    			+ " minutes" ) ;

		closeLogFile() ;
	} // main()

    /**
     * Check for the presence of a file named stopFileName. If present, return true.
     * @return
     */
    public synchronized static boolean stopExecution()
    {
    	return stopExecution( stopFileName ) ;
    }

    public synchronized static boolean stopExecution( final String fileName )
    {
    	final File stopFile = new File( fileName ) ;
    	boolean fileExists = stopFile.exists() ;
    	return fileExists ;
    }

    public static List< TranscodeFile > surveyInputDirectoryAndBuildTranscodeFiles( final String inputDirectory )
    {
    	assert( inputDirectory != null ) ;

    	List< TranscodeFile > filesToTranscode = new ArrayList< >() ;

    	// inputDirectory could be:
    	// - Invalid path
    	// - Empty
    	// - Contain one or more movies
    	// - Contain one or more TV Shows
    	// - Contain one or more movies and one or more TV Shows

    	File inputDirectoryFile = new File( inputDirectory ) ;
    	if( !inputDirectoryFile.exists() )
    	{
    		out( "surveyInputDirectoryAndBuildTranscodeFiles> inputDirectory does not exist: " + inputDirectory ) ;
    		return filesToTranscode ;
    	}

    	// First, look for files in the inputDirectory with extensions listed in transcodeExtensions
    	filesToTranscode.addAll( getTranscodeFilesInDirectory( inputDirectory ) ) ;
    	return filesToTranscode ;
    }

    public static List< TranscodeFile > getTranscodeFilesInDirectory( final String inputDirectory )
    {
    	return getTranscodeFilesInDirectory( new File( inputDirectory ) ) ;
    }

    public static List< TranscodeFile > getTranscodeFilesInDirectory( final File inputDirectory )
    {
    	List< TranscodeFile > transcodeFilesInDirectory = new ArrayList< >() ;
    	for( String extension : transcodeExtensions )
    	{
    		List< File > filesByExtension = getFilesInDirectoryWithExtension( inputDirectory.getAbsolutePath(), extension ) ;
    		for( File theFile : filesByExtension )
    		{
    			TranscodeFile newTranscodeFile = new TranscodeFile( theFile, mkvFinalDirectory, mp4OutputDirectory, mp4FinalDirectory ) ;
    			transcodeFilesInDirectory.add( newTranscodeFile ) ;
    		}
    	}
    	return transcodeFilesInDirectory ;
    }

    public static void executeCommand( ImmutableList.Builder< String > theCommand )
    {
    	executeCommand( toStringForCommandExecution( theCommand.build() ) ) ;
    }
    
	public static void executeCommand( final String theCommand )
	{
		out( "executeCommand> " + theCommand ) ;

		// Only execute the command if we are NOT in test mode
		if( !testMode )
		{
			try
			{
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				final Process process = Runtime.getRuntime().exec( theCommand ) ;

				BufferedReader errorStreamReader = new BufferedReader( new InputStreamReader( process.getErrorStream() ) ) ;
				String line = null ;
				while( (line = errorStreamReader.readLine()) != null )
				{

					out( "executeCommand> ErrorStream: " + line ) ;
				}
			}
			catch( Exception theException )
			{
				theException.printStackTrace() ;
			}
		}
	}

	public static List< File > getFilesInDirectoryWithExtension( final String directoryPath, final String extension )
	{
		List< File > filesInDirectoryWithExtension = new ArrayList< >() ;
		try
		{
			Stream< Path > walk = Files.walk( Paths.get( directoryPath ) ) ;
			List< String > fileNames = walk.filter( Files::isRegularFile ).map( x -> x.toString() ).collect( Collectors.toList() ) ;
			walk.close() ;

			// Filter by extension
			for( String fileName : fileNames )
			{
				if( fileName.endsWith( extension ) )
				{
					filesInDirectoryWithExtension.add( new File( fileName ) ) ;
				}
			}
		} // try()
		catch( Exception theException )
		{
			out( "getFilesInDirectoryWithExtension (" + directoryPath + ")> Exception: " + theException ) ;
		}
		return filesInDirectoryWithExtension ;
	}

    static String getPathSeparator()
	{
		String retMe = pathSeparator ;
		if( !isWindows )
		{
			// Linux naming
			retMe = "\\\\" ;
		}
		return retMe ;
	}

	/**
	     * Retrieve all subdirectories to the given directoryPath.
	     * Note that this excludes the parent directory.
	     * Example: directoryPath = F:/Movies, return all directories underneath it (like "Elf" and "Mr. Deeds"), but not the
	     * directory itself (F:/Movies)
	     * @param directoryPath
	     * @return
	     */
    public static List< File > getSubDirectories( final String directoryPath )
    {
    	return getSubDirectories( new File( directoryPath ) ) ;
    }

    public static List< File > getSubDirectories( final File directoryPathFile )
    {
    	File[] directories = directoryPathFile.listFiles( File::isDirectory ) ;
    	return Arrays.asList( directories ) ;
    }

	static boolean hasInputFileInDirectory( final File theDirectory )
	{
		for( String extension : transcodeExtensions )
		{
			if( hasInputFileInDirectory( theDirectory, extension ) )
			{
				return true ;
			}
		}
		return false ;
	}

	static boolean hasInputFileInDirectory( final File theDirectory, final String extension )
	{
		return hasInputFileInDirectory( theDirectory.getAbsolutePath(), extension ) ;
	}

	static boolean hasInputFileInDirectory( final String directoryName, final String extension )
	{
		List< File > inputFileNameList = getFilesInDirectoryWithExtension( directoryName, extension ) ;
		return (inputFileNameList.size() > 0) ;
	}

	static void makeDirectory( final String directoryName )
	{
		try
		{
			File directoryFile = new File( directoryName ) ;
			if( !directoryFile.exists() )
			{
				out( "makeDirectory> Making directory structure: " + directoryName ) ;
				if( !testMode && !directoryFile.mkdirs() )
				{
					out( "makeDirectory> Unable to mkdirs (" + directoryName + ")" ) ;
				}
			}

		}
		catch( Exception theException )
		{
			out( "makeDirectory> Exception: (\"" + directoryName + "\"): " + theException.toString() ) ;
		}
	}

    public static String addPathSeparatorIfNecessary( String inputPath )
    {
    	String retMe = inputPath ;
    	if( !inputPath.endsWith( getPathSeparator() ) )
		{
			retMe = inputPath + getPathSeparator() ;
		}
    	return retMe ;
    }

	/**
     * Replace the extension in the given inputFileName with the provided new extension.
     * @param inputFileName
     * @return
     */
    public static String replaceFileNameExtension( String inputFileName, final String newExtension )
    {
    	// Low likelihood that a filename has any of the stored input extensions as part of its name.
    	for( String inputExtension : transcodeExtensions )
    	{
    		inputFileName = inputFileName.replace( inputExtension, newExtension ) ;
    	}
    	return inputFileName ;
    }

    public static List< TranscodeFile > orderFilesToTranscode( final List< TranscodeFile > theFiles )
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

    public static void buildDirectories( final TranscodeFile inputFile )
    {
    	makeDirectory( inputFile.getMkvFinalDirectory() ) ;
    	makeDirectory( inputFile.getMp4OutputDirectory() ) ;
    	makeDirectory( inputFile.getMp4FinalDirectory() ) ;
    }

    public static void transcodeFile( TranscodeFile inputFile, FFmpegProbeResult ffmpegProbeResult )
    {
    	// Precondition: ffmpegProbeResult is not null
    	out( "transcodeFile> Transcoding: " + inputFile ) ;

    	// Note that the audio and subtitle stream mapping works differently from each other.
    	// Audio streams are left included in the input file, and any that don't fit the transcode style
    	//  (6.1, 7.2, non-eng, etc.) are *excluded* in the transcode options.
    	// That is, they are excluded explicitly here.
    	List< Integer > excludeAudioStreamIndices = ExtractPGSFromMKVs.findExcludedAudioStreamsAsInteger( ffmpegProbeResult.streams ) ;

    	// subtitle streams are expected to already be extracted appropriately.
    	// As such, our action for subtitles is to explicitly *include* them during the transcode.
    	ImmutableList.Builder< String > localTranscodeSubTitleOptions = buildSRTOptions( inputFile ) ;
    	
//    	out( "transcodeFile> localTranscodeSubTitleOptions: " + localTranscodeSubTitleOptions ) ;

		// Perform the options build in four steps:
		//  1) Setup ffmpeg basic options
		//  2) Include source file
		//  3) Include all other input files (such as .srt)
    	//  4) Adding default mapping option
		//  5) Add video transcode options
		//  6) Add metadata transcode options
		//  7) Add audio transcode options
		//  8) Add subtitle transcode options
		//  9) Add output filename (.mp4)
    	
    	// ffmpegCommand will hold the command to execute ffmpeg
    	ImmutableList.Builder< String > ffmpegCommand = new ImmutableList.Builder<String>() ;
    	
    	// 1) Setup ffmpeg basic options
		ffmpegCommand.add( pathToFFMPEG ) ;
		
		// Overwrite existing files
		ffmpegCommand.add( "-y" ) ;
		
		// 2) Include source file
		ffmpegCommand.add( "-i", inputFile.getMKVFileNameWithPath() ) ;

		// 3) Include all other input files (such as .srt)
		for( File srtFile : inputFile.srtFileList )
		{
			ffmpegCommand.add( "-i", srtFile.getAbsolutePath() ) ;
		}
		
		//  4) Adding default mapping option
		ffmpegCommand.add( "-map", "0" ) ;
		
		//  5) Add video transcode options
		ffmpegCommand.addAll( transcodeOptions.build() ) ;
		
		//  6) Add metadata transcode options
		ffmpegCommand.add( "-metadata", "title=\"" + inputFile.getMetaDataTitle() + "\"" ) ;
			
		//  7) Add audio transcode options
		for( Integer excludeStreamInteger : excludeAudioStreamIndices )
		{
			// Exclude each audio stream that is not permitted.
			final String excludeStreamString = excludeStreamInteger.toString() ;
			ffmpegCommand.add( "-map", "-0:" + excludeStreamString ) ;
		}
		
		//  8) Add subtitle transcode options
		ffmpegCommand.addAll( localTranscodeSubTitleOptions.build() ) ;
		
		//  9) Add output filename (.mp4)
		ffmpegCommand.add( inputFile.getMP4OutputFileNameWithPath() ) ;

    	long startTime = System.nanoTime() ;
    	out( toStringForCommandExecution( ffmpegCommand.build() ) ) ;

    	// Execute the transcode
    	if( testMode )
    	{
    		out( "transcodeFile> Executing command: " + toStringForCommandExecution( ffmpegCommand.build() ) ) ;
    	}
    	else
    	{
    		executeCommand( ffmpegCommand ) ;
    	}

    	long endTime = System.nanoTime() ; double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;

    	double timePerGigaByte = timeElapsedInSeconds / (inputFile.getInputFileSize() / 1000000000.0) ;
    	out( "transcodeFile> Elapsed time to transcode "
    			+ inputFile.getMkvFileName()
    			+ ": "
    			+ numFormat.format( timeElapsedInSeconds )
    			+ " seconds, "
    			+ numFormat.format( timeElapsedInSeconds / 60.0 )
    			+ " minutes, or "
    			+ numFormat.format( timePerGigaByte )
    			+ " seconds per GB" ) ;

    	// Move the MKV file to its final directory
    	if( inputFile.getMKVFileShouldMove() )
    	{
    		moveFile( inputFile.getMKVFileNameWithPath(), inputFile.getMkvFinalFileNameWithPath() ) ;
    		for( File srtFile : inputFile.srtFileList )
    		{
    			moveFile( srtFile.getAbsolutePath(), inputFile.getMkvFinalFileNameWithPath() ) ;
    		}
    	}
    	if( inputFile.getMP4FileShouldMove() )
    	{
    		moveFile( inputFile.getMp4OutputFileNameWithPath(), inputFile.getMP4FinalOutputFileNameWithPath() ) ;
    	}
    }
    
    /**
     * Build a list of ffmpeg mapping options to incorporate any subtitle (.srt) files into the transcode.
     * Excludes importing the srt files themselves.
     * @param theTranscodeFile
     * @return
     */
    public static ImmutableList.Builder< String > buildSRTOptions( TranscodeFile theTranscodeFile )
    {
    	// "${srtInputFiles[@]}" -map 0:v -map 0:a -map -0:s "${srtMapping[@]}" -copyts
    	ImmutableList.Builder< String > subTitleOptions = new ImmutableList.Builder< String >() ;

    	// Check if we found any srt files
    	if( !theTranscodeFile.srtFileList.isEmpty() )
    	{
    		// Next, add the mapping for video and audio for index 0, and remove subtitles from index 0
    		subTitleOptions.add( "-map", "-0:s" ) ;
    		
    		// Now add the mapping for each input file
    		// Note: This assumes the same iteration for this loop as with the input options.
    		// In practice it doesn't matter since each of the SRT input files only has a single input stream.
    		for( int mappingIndex = 1 ; mappingIndex <= theTranscodeFile.srtFileList.size() ; ++mappingIndex )
    		{
    			subTitleOptions.add( "-map", "" + mappingIndex + ":s" ) ;
    		}

    		// Finally, add the options to copy the timestamp and use the mov_text subtitle codec
    		// TODO: May need to update this based on use of other codecs (dvds, etc.)
    		subTitleOptions.add( "-c:s", "mov_text" ) ;
    	}
    	return subTitleOptions ;
    }
    
    public static void moveFile( final String sourceFileName, final String destinationFileName )
    {
    	boolean doMove = false ;
    	if( sourceFileName.endsWith( ".mkv" ) ) doMove = doMoveMKV ;
    	else if( sourceFileName.endsWith( ".mp4" ) ) doMove = doMoveMP4 ;
    	else if( sourceFileName.endsWith( ".srt" ) ) doMove = doMoveSRT ;
    	else
    	{
    		out( "moveFile> Unable to find move boolean for input file: " + sourceFileName ) ;
    	}
    	
    	if( !sourceFileName.equalsIgnoreCase( destinationFileName ) && doMove )
    	{
	    	MoveFileThreadAction theMoveFileThreadAction =
	    			new MoveFileThreadAction( sourceFileName, destinationFileName ) ;

	    	int workerThreadsIndex = mkvMoveThreadIndex ;
	    	if( sourceFileName.contains( ".mp4" ) )
	    	{
	    		workerThreadsIndex = mp4MoveThreadIndex ;
	    	}

	    	// Move the input file to its archive location
	    	workerThreads[ workerThreadsIndex ].addWork( theMoveFileThreadAction ) ;
	    }
    }
    
    static boolean fileExists( final String fileNameWithPath )
    {
    	final File theFile = new File( fileNameWithPath ) ;
    	return theFile.exists() ;
    }

    static synchronized void out( final String outputMe )
    {
    	System.out.println( outputMe ) ;
    	log( outputMe ) ;
    }

    static synchronized void out( final List< TranscodeFile > theFiles )
    {
    	for( TranscodeFile theFile : theFiles )
    	{
    		out( theFile.toString() ) ;
    	}
    }
    
    static synchronized void log( final String logMe )
    {
    	if( logWriter != null )
    	{
    		try
    		{
    			logWriter.write( logMe ) ;
    			logWriter.newLine() ;
    		}
    		catch( Exception theException )
    		{
    			System.out.println( "log> Unable to write to logWriter: " + theException ) ;
    		}
    	}
    }

    static void openLogFile()
    {
    	openLogFile( logWriterFileName ) ;
    }

    static void openLogFile( final String fileName )
    {
    	try
    	{
    		logWriter = new BufferedWriter( new FileWriter( fileName ) ) ;
    	}
    	catch( Exception theException )
    	{
    		logWriter = null ;
    		out( "openLogFile> Exception opening logWriter: " + theException ) ;
    	}
    }

    static void closeLogFile()
    {
    	try
    	{
    		if( logWriter != null )
    		{
    			logWriter.close() ;
    		}
    	}
    	catch( Exception theException )
    	{
    		out( "closeLogFile> Exception closing logWriter: " + theException ) ;
    	}
		logWriter = null ;
    }
    

	static String toStringForCommandExecution( final ImmutableList< String > theList )
	{
		String retMe = "" ;
		for( Iterator< String > listIterator = theList.iterator() ; listIterator.hasNext() ; )
//		for( String listItem : theList )
		{
			// Any file names with spaces must be encapsulated in double quotes, except for those
			// items that already start with "
			String arg = listIterator.next();
			if( arg.contains( " " ) && !arg.startsWith( "\"" ) && !arg.endsWith( "\"" ) )
			{
				retMe += "\"" ;
			}
			retMe += arg ;

			// Any file names with spaces must be encapsulated in double quotes, except for those
			// items that already start with "
			if( arg.contains( " " ) && !arg.startsWith( "\"" ) && !arg.endsWith( "\"" ) )
			{
				retMe += "\"" ;
			}
			
			if( listIterator.hasNext() )
			{
				// At least one more item remaining, add a space
				retMe += " " ;
			}
		}
		return retMe ;
	}
	
	static void touchFile( final String fileName )
	{
		try
		{
			File theTouchFile = new File( fileName ) ;
			if( !theTouchFile.exists() )
			{
				theTouchFile.createNewFile() ;
			}
		}
		catch( Exception e )
		{
			run_ffmpeg.out( "TranscodeFile.touchFile> Exception for file " + fileName + ": " + e ) ;
		}
	}

} // run_ffmpeg
