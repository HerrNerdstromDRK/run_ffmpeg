package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import com.google.common.collect.ImmutableList;

/**
 * Problems to solve:
 * - Identify which movies/tv shows are missing MKVs
 * -- Build an inventory algorith to list all movies/tv shows
 * - Identify which movies/tv shows are missing subtitles
 * -- Build ffprobe for both input and output
 * --- Need means to record them
 * - Identify which movies/tv shows need forced subtitles
 * - Fix subtitles, audio, metadata for all items, if required
 * -- Need to know which are broken
 * -- Need to correlate those back to the MKV files and cross-check
 * - Build a method to update those movies/tv shows that are misconfigured or missing information
 * -- Use above database (?) of ffprobe data to analyze deltas
 * -- Update run_ffmpeg (or other) to fix those items using database inputs
 * @author Dan
 */
public class ExtractPGSFromMKVs extends Thread
{
	private List< String > drivesAndFoldersToExtract = null ;

	/// Directory from which to read MKV files
	//	static String mkvInputDirectory = "C:\\Temp\\Little Women (2019)" ;
	private String mkvInputDirectory = "\\\\yoda\\MKV_Archive7\\Movies" ;

	//	private String mkvInputDirectory = "D:\\Temp\\Big Bang" ;
	//	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive5\\TV Shows\\Game Of Thrones" ;

	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;
	private transient TranscodeCommon transcodeCommon = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_extract_pgs.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_pgs.txt" ;

	static final String codecTypeSubTitleNameString = "subtitle" ;
	static final String codecNameSubTitlePGSString = "hdmv_pgs_subtitle" ;
	static final String codecNameSubTitleSRTString = "subrip" ;
	static final String codecNameSubTitleDVDSubString = "dvd_subtitle" ;

	/// Identify the allowable languages for subtitles.
	static final String[] allowableSubTitleLanguages = {
			"eng",
			"en"
	} ;

	/// These subtitle codec names to be extracted.
	/// This will mostly be used when selecting which streams to extract as separate files.
	static final String[] extractableSubTitleCodecNames = {
			codecNameSubTitlePGSString,
			codecNameSubTitleSRTString,
			codecNameSubTitleDVDSubString
	} ;

	public ExtractPGSFromMKVs()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		transcodeCommon = new TranscodeCommon( log, common, "", "", "", "" ) ;

		// The default set of drives and folders to extract is all of them.
		setDrivesAndFoldersToExtract( common.getAllMKVDrivesAndFolders() ) ;
	}

	public static void main(String[] args)
	{
		boolean useTwoThreads = true ;
		ExtractPGSFromMKVs extractPGS = new ExtractPGSFromMKVs() ;

		/**
		 * The only difference between these two branches is that running with threads
		 * will set one chain of drives and folders to each thread and run,
		 * and running without threads will just run in a single thread.
		 * For now, running as a single thread will leave drives and folders as null,
		 * and so the mkvInputdirectory will be used.
		 */
		if( useTwoThreads )
		{
			System.out.println( "ExtractPGSFromMKVs.main> Running with threads" ) ;
			extractPGS.runThreads() ;
		}
		else
		{
			System.out.println( "ExtractPGSFromMKVs.main> Running without threads" ) ;
			extractPGS.run() ;
		}

		System.out.println( "ExtractPGSFromMKVs.main> Shutdown." ) ;
	}

	private void runThreads()
	{
		ExtractPGSFromMKVs extractPGS1 = new ExtractPGSFromMKVs() ;
		ExtractPGSFromMKVs extractPGS2 = new ExtractPGSFromMKVs() ;

		extractPGS1.setChainA() ;
		extractPGS2.setChainB() ;

		log.info( "Starting threads." ) ;
		extractPGS1.start() ;
		extractPGS2.start() ;
		log.info( "Running threads..." ) ;

		try
		{
			// Set the stop file to halt execution
			while( extractPGS1.shouldKeepRunning()
					&& extractPGS1.isAlive()
					&& extractPGS2.isAlive() )
			{
				Thread.sleep( 100 ) ;
			} // while( keepRunning )

			log.info( "Shutting down threads..." ) ;
			extractPGS1.join() ;
			extractPGS2.join() ;
		}
		catch( Exception e )
		{
			log.info( "Exception: " + e.toString() ) ;
		}
	}

	@Override
	public void run()
	{
		List< String > drivesAndFolders = getDrivesAndFoldersToExtract() ;
		log.info( "Extracting from drives and folders: " + drivesAndFolders.toString() ) ;

		final long startTime = System.nanoTime() ;
		for( String folderName : drivesAndFolders )
		{
			log.info( "Extracting subtitle files from folder " + folderName ) ;
			if( !shouldKeepRunning() )
			{
				break ;
			}

			runOne( folderName ) ;
			log.info( "Completed extracting subtitle files from folder " + folderName ) ;
		}
		final long endTime = System.nanoTime() ;

		log.info( "Completed extracting from drives and folders: " + drivesAndFolders.toString() ) ;
		log.info( common.makeElapsedTimeString( startTime, endTime ) ) ;
		log.info( "Thread shutdown." ) ;
	}

	public void runOne( final String inputDirectory )
	{
		transcodeCommon.setMkvInputDirectory( inputDirectory ) ;

		// First, survey the input directory for files to process, and build
		// a TranscodeFile object for each.
		List< TranscodeFile > filesToProcess = transcodeCommon.surveyInputDirectoryAndBuildTranscodeFiles( inputDirectory,
				transcodeCommon.getTranscodeExtensions() ) ;

		// Perform the core work of this application
		for( TranscodeFile theFileToProcess : filesToProcess )
		{
			if( !shouldKeepRunning() )

			{
				log.info( "Stopping execution due to presence of stop file: " + getStopFileName() ) ;
				break ;
			}

			// Skip this file if a .srt file exists in its directory
			if( theFileToProcess.hasSRTInputFiles() || theFileToProcess.hasSUPInputFiles() )
			{
				log.info( "Skipping file due to presence of SRT or SUP file: " + theFileToProcess.getMkvFileName() ) ;
				continue ;
			}

			// Look for usable subtitle streams in the file and retrieve a list of options
			// for an ffmpeg extract command
			FFmpegProbeResult probeResult = common.ffprobeFile( theFileToProcess, log ) ;
			if( null == probeResult )
			{
				// Unable to ffprobe the file
				log.warning( "Error probing file: \"" + theFileToProcess.getMKVFileNameWithPath() + "\"" ) ;
				continue ;
			}

			extractSubtitles( theFileToProcess, probeResult ) ;
		} // for( fileToSubTitleExtract )
	}

	public ImmutableList.Builder<String> buildFFmpegSubTitleExtractionOptionsString( FFmpegProbeResult probeResult,
			TranscodeFile theFile )
	{
		// The ffmpegOptionsCommandString will contain only the options to extract subtitles
		// from the given FFmpegProbeResult
		// All of the actual ffmpeg command build ("ffmpeg -i ...") happens elsewhere
		ImmutableList.Builder<String> ffmpegOptionsCommandString = new ImmutableList.Builder<String>();

		// includedSubTitleStreams will include only allowable subtitle streams to extract
		List< FFmpegStream > extractableSubTitleStreams = findExtractableSubTitleStreams( probeResult ) ;
		for( FFmpegStream stStream : extractableSubTitleStreams )
		{
			final int streamIndex = stStream.index ;

			// So far, I know of two subtitle types I can work with: pgs and srt
			// -map 0:$streamNumber -c:s copy "$supFileName"

			// Found PGS or SRT stream
			ffmpegOptionsCommandString.add( "-map", "0:" + streamIndex ) ;

			// Create the .sup filename
			// First, replace the .mkv with empty string: Movie (2000).mkv -> Movie (2009)
			//				String outputFileName = theFile.getMKVFileNameWithPath().replace( ".mkv", "" ) ;
			String outputPath = theFile.getMKVInputPath() ;
			String outputFileNameWithPath = common.addPathSeparatorIfNecessary( outputPath )
					+ theFile.getMkvFileName().replace( ".mkv", "" ) ;

			// Movie (2009) -> Movie (2009).1.sup or Movie (2009).1.srt
			outputFileNameWithPath += "." + streamIndex ;
			if( stStream.codec_name.equals( codecNameSubTitlePGSString ) )
			{
				outputFileNameWithPath += ".sup" ;
			}
			else if( stStream.codec_name.equals( codecNameSubTitleSRTString ) )
			{
				outputFileNameWithPath += ".srt" ;
			}
			
//			if( stStream.codec_name.equals( codecNameSubTitleDVDSubString ) )
//			{
//				ffmpegOptionsCommandString.add( "-c:s", "dvdsub" ) ;
//				ffmpegOptionsCommandString.add( "-f", "rawvideo", outputFileNameWithPath ) ;
//			}
//			else
//			{
				ffmpegOptionsCommandString.add( "-c:s", "copy", outputFileNameWithPath ) ;
//			}
		}
		log.fine( "ffmpegOptionsCommandString: "
				+ common.toStringForCommandExecution( ffmpegOptionsCommandString.build() ) ) ;
		return ffmpegOptionsCommandString ;	
	}

	public void extractSubtitles( TranscodeFile fileToSubTitleExtract, FFmpegProbeResult probeResult )
	{
		// Build a set of options for an ffmpeg command based on the JSON input
		// If no suitable subtitles are found, the options string will be empty
		ImmutableList.Builder<String> subTitleExtractionOptionsString =
				buildFFmpegSubTitleExtractionOptionsString( probeResult, fileToSubTitleExtract ) ;

		// If subTitleExtractionOptionsString is empty, then no usable subtitle streams were found
		if( subTitleExtractionOptionsString.build().isEmpty() )
		{
			// No usable streams found
			// Skip this file
			return ;
		}

		// Build the ffmpeg command
		ImmutableList.Builder< String > ffmpegSubTitleExtractCommand = new ImmutableList.Builder<String>() ;
		ffmpegSubTitleExtractCommand.add( Common.getPathToFFmpeg() ) ;
		ffmpegSubTitleExtractCommand.add( "-y" ) ;
		ffmpegSubTitleExtractCommand.add( "-i", fileToSubTitleExtract.getMKVFileNameWithPath() ) ;
		ffmpegSubTitleExtractCommand.addAll( subTitleExtractionOptionsString.build() ) ;

		common.executeCommand( ffmpegSubTitleExtractCommand ) ;
		
		// Unable to know if a subtitle stream is valid before it is written.
		// Prune any small .sup files created herein.
		PruneSmallSUPFiles pssf = new PruneSmallSUPFiles() ;
		File regularFile = new File( fileToSubTitleExtract.getMKVFileNameWithPath() ) ;
		pssf.pruneFolder( regularFile.getAbsolutePath() ) ;
	}

	/**
	 * Walk through the list of streams, find and return the subtitle streams that can be extracted.
	 * These are generally the PGS and SRT streams, and in English only.
	 * Will always return a non-null list, although it may be empty.
	 * @param probeResult
	 * @return
	 */
	public List< FFmpegStream > findExtractableSubTitleStreams( FFmpegProbeResult probeResult )
	{
		List< FFmpegStream > extractableSubTitleStreams = new ArrayList< FFmpegStream >() ;

		for( FFmpegStream theStream : probeResult.streams )
		{
			log.fine( "Checking stream: " + theStream ) ;
			if( !theStream.codec_type.equals( codecTypeSubTitleNameString ) )
			{
				// Not a subtitle stream
				log.fine( "Ignoring stream: " + theStream ) ;
				continue ;
			}
			// Post condition: theStream is a subtitle stream

			// Check for a language tag
			if( theStream.tags.containsKey( "language" ) )
			{
				boolean isAllowableLanguage = isAllowableSubTitleLanguage( theStream.tags.get( "language" ) ) ;
				if( !isAllowableLanguage )
				{
					// Has a language tag, but it is not allowable
					log.fine( "Found subtitle with language tag but NOT allowable: "
							+ theStream.tags.get( "language" ) ) ;
					continue ;
				}
			}
			// Post condition: this is a subtitle stream with an allowable language

			if( isExtractableSubTitleCodecName( theStream.codec_name ) )
			{
				// Found allowable subtitle type
				log.info( "Found allowable subtitle stream: " + theStream ) ;
				extractableSubTitleStreams.add( theStream ) ;
			}
		} // for( stream )
		return extractableSubTitleStreams ;
	}

	static boolean isAllowableSubTitleLanguage( final String audioLanguage )
	{
		for( String allowableLanguage : allowableSubTitleLanguages )
		{
			if( allowableLanguage.equalsIgnoreCase( audioLanguage ) )
			{
				// Found an allowable language
				return true ;
			}
		}
		// No allowable language found
		return false ;
	}

	static boolean isExtractableSubTitleCodecName( final String stCodeName )
	{
		for( String allowableCodecName : extractableSubTitleCodecNames )
		{
			if( allowableCodecName.equalsIgnoreCase( stCodeName ) )
			{
				// Found an allowable code name
				return true ;
			}
		}
		// No allowable code name found
		return false ;
	}

	public String getMkvInputDirectory() {
		return mkvInputDirectory;
	}

	public String getStopFileName() {
		return stopFileName;
	}

	public List< String > getDrivesAndFoldersToExtract() {
		List< String > retMe = new ArrayList< String >() ;
		if( drivesAndFoldersToExtract != null )
		{
			retMe.addAll( drivesAndFoldersToExtract ) ;
		}
		else
		{
			retMe.add( getMkvInputDirectory() ) ;
		}
		return retMe;
	}

	/**
	 * Tell this instance to execute only chain A.
	 */
	public void setChainA()
	{
		List< String > drivesAndFoldersToProbe = new ArrayList< String >() ;
		drivesAndFoldersToProbe.addAll( common.getAllChainAMKVDrivesAndFolders() ) ;
		drivesAndFoldersToProbe.addAll( common.getAllChainAMP4DrivesAndFolders() ) ;
		setDrivesAndFoldersToExtract( drivesAndFoldersToProbe ) ;
	}

	/**
	 * Tell this instance to execute only chain B.
	 */
	public void setChainB()
	{
		List< String > drivesAndFoldersToProbe = new ArrayList< String >() ;
		drivesAndFoldersToProbe.addAll( common.getAllChainBMKVDrivesAndFolders() ) ;
		drivesAndFoldersToProbe.addAll( common.getAllChainBMP4DrivesAndFolders() ) ;
		setDrivesAndFoldersToExtract( drivesAndFoldersToProbe ) ;
	}

	public void setDrivesAndFoldersToExtract(List<String> drivesAndFoldersToExtract) {
		this.drivesAndFoldersToExtract = drivesAndFoldersToExtract;
	}

	public void setMkvInputDirectory(String mkvInputDirectory) {
		this.mkvInputDirectory = mkvInputDirectory;
	}

	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( getStopFileName() ) ;
	}

}
