package run_ffmpeg;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
 *
 */
public class ExtractPGSFromMKVs
{
	/// Directory from which to read MKV files
	//	static String mkvInputDirectory = "C:\\Temp\\Little Women (2019)" ;
//	private String mkvInputDirectory = "\\\\yoda\\MKV_Archive1" ;
	private String mkvInputDirectory = "E:\\Movies" ;
	//	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive5\\TV Shows\\Game Of Thrones" ;

	/// Set to true to place the output SRT files into the same directory
	/// in which the input files are found.
	/// When set to true, the below destination directory will be ignored.
	private boolean doPlaceSRTFilesInInputDirectory = true ;

	/// Directory to which to write .srt files
	private final String subTitleStreamExtractDestinationDirectory = mkvInputDirectory ;

	/// Set testMode to true to prevent mutations
	static boolean testMode = false ;

	/// Set to true to extract the subtitles from this file into one or more separate subtitle files
	private final boolean doSubTitleExtract = true ;

	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_extract_pgs.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_pgs.txt" ;

	static final String pathToFFMPEG = run_ffmpeg.pathToFFMPEG ;
	static final String pathToFFPROBE = run_ffmpeg.pathToFFPROBE ;
	static final String codecTypeSubTitleNameString = "subtitle" ;
	static final String codecNameSubTitlePGSString = "hdmv_pgs_subtitle" ;
	static final String codecNameSubTitleSRTString = "subrip" ;
	static final String codecNameSubTitleDVDSubString = "dvd_subtitle" ;
	static final String codecTypeAudio = "audio" ;

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

	/// The list of subtitle code names that should be left in any files to be transcoded directly
	static final String[] transcodeableSubTitleCodecNames = {
			codecNameSubTitleSRTString
	} ;

	public ExtractPGSFromMKVs()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		run_ffmpeg.testMode = testMode ;
	}

	public static void main(String[] args)
	{
		ExtractPGSFromMKVs extractPGS = new ExtractPGSFromMKVs() ;
		extractPGS.run() ;
	}

	public void run()
	{
		// First, survey the input directory for files to process, and build
		// a TranscodeFile object for each.
		List< TranscodeFile > filesToProcess = run_ffmpeg.surveyInputDirectoryAndBuildTranscodeFiles( mkvInputDirectory ) ;

		// Perform the core work of this application
		for( TranscodeFile theFileToProcess : filesToProcess )
		{
			if( run_ffmpeg.stopExecution( stopFileName ) )
			{
				log.info( "Stopping execution due to presence of stop file: " + stopFileName ) ;
				break ;
			}

			// Skip this file if a .srt file exists in its directory
			if( theFileToProcess.hasSRTInputFiles() )
			{
				log.info( "Skipping file due to presenece of SRT or SUP file: " + theFileToProcess.getMkvFileName() ) ;
				continue ;
			}

			// Look for usable subtitle streams in the file and retrieve a list of options
			// for an ffmpeg extract command
			FFmpegProbeResult probeResult = ffprobeFile( theFileToProcess, log ) ;
			if( null == probeResult )
			{
				// Unable to ffprobe the file
				log.warning( "Error probing file: \"" + theFileToProcess.getMKVFileNameWithPath() + "\"" ) ;
				continue ;
			}

			if( doSubTitleExtract )
			{
				extractSubtitles( theFileToProcess, probeResult ) ;
			}

		} // for( fileToSubTitleExtract )
		log.info( "Complete." ) ;
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
			String outputPath = subTitleStreamExtractDestinationDirectory ;
			if( doPlaceSRTFilesInInputDirectory )
			{
				// Place the subtitle files in the same directory as the source files
				outputPath = theFile.getMKVInputPath() ;
			}
			String outputFileNameWithPath = run_ffmpeg.addPathSeparatorIfNecessary( outputPath )
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
			ffmpegOptionsCommandString.add( "-c:s", "copy", outputFileNameWithPath ) ;

		}
//		log.info( "ffmpegOptionsCommandString: "
//				+ run_ffmpeg.toStringForCommandExecution( ffmpegOptionsCommandString.build() ) ) ;
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
		ImmutableList.Builder<String> ffmpegSubTitleExtractCommand = new ImmutableList.Builder<String>() ;
		ffmpegSubTitleExtractCommand.add( pathToFFMPEG ) ;
		ffmpegSubTitleExtractCommand.add( "-y" ) ;
		ffmpegSubTitleExtractCommand.add( "-i", fileToSubTitleExtract.getMKVFileNameWithPath() ) ;
		ffmpegSubTitleExtractCommand.addAll( subTitleExtractionOptionsString.build() ) ;

		run_ffmpeg.executeCommand( ffmpegSubTitleExtractCommand ) ;
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

	/**
	 * Run ffprobe on the given file.
	 * If an error occurs, return null. 
	 * Otherwise, return the FFmpegProbeResult from the ffprobe.
	 */
	public static FFmpegProbeResult ffprobeFile( TranscodeFile theFile, Logger log )
	{
		return ffprobeFile( new File( theFile.getMKVFileNameWithPath() ), log ) ;
	}

	public static FFmpegProbeResult ffprobeFile( File theFile, Logger log )
	{	
		log.info( "Processing: " + theFile.getAbsolutePath() ) ;
		FFmpegProbeResult result = null ;

		ImmutableList.Builder<String> ffprobeExecuteCommand = new ImmutableList.Builder<String>();
		ffprobeExecuteCommand.add( pathToFFPROBE ) ;

		// Add option "-v quiet" to suppress the normal ffprobe output
		ffprobeExecuteCommand.add( "-v", "quiet" ) ;

		// Instruct ffprobe to show streams
		ffprobeExecuteCommand.add( "-show_streams" ) ;

		// Instruct ffprobe to return result as json
		ffprobeExecuteCommand.add( "-print_format", "json" ) ;

		// Finally, add the input file
		ffprobeExecuteCommand.add( "-i", theFile.getAbsolutePath() ) ;

		// Build the GSON parser for the JSON input
		GsonBuilder builder = new GsonBuilder(); 
		builder.setPrettyPrinting(); 
		Gson gson = builder.create();

		try
		{
			Thread.currentThread().setPriority( Thread.MIN_PRIORITY ) ;
			String ffprobeExecuteCommandString = run_ffmpeg.toStringForCommandExecution( ffprobeExecuteCommand.build() ) ;
			log.info( "Execute ffprobe command: " + ffprobeExecuteCommandString ) ;

			final Process process = Runtime.getRuntime().exec( ffprobeExecuteCommandString ) ;

			BufferedReader inputStreamReader = new BufferedReader( new InputStreamReader( process.getInputStream() ) ) ;
			int lineNumber = 1 ;
			String inputLine = null ;
			String inputBuffer = "" ;
			while( (inputLine = inputStreamReader.readLine()) != null )
			{
				log.fine( "" + lineNumber + "> " + inputLine ) ;
				inputBuffer += inputLine ;
				++lineNumber ;
			}

			if( process.exitValue() != 0 )
			{
				log.warning( "Error running ffprobe on file " + theFile.getAbsolutePath() + "; exitValue: " + process.exitValue() ) ;
				result = null ; // already null, but just for clarity
			}
			else
			{
				// Deserialize the JSON streams info from this file
				result = gson.fromJson(inputBuffer, FFmpegProbeResult.class);
				// TODO: Ensure consistent file path naming using \\yoda as start
				result.setFilename(theFile.getAbsolutePath());
				result.setProbeTime(System.currentTimeMillis());
				result.setSize(theFile.length());
			}
		}
		catch( Exception theException )
		{
			theException.printStackTrace() ;
		}
		return result ;
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
}
