package run_ffmpeg;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ExtractPGSFromMKVs
{
	/// Directory from which to read MKV files
//	static String mkvInputDirectory = "C:\\Temp\\7.1 BlacKkKlansman (2018)" ;
	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive6\\To Convert\\Fury (2014)" ;
//	static String mkvInputDirectory = "\\\\yoda\\MKV_Archive5\\TV Shows\\Game Of Thrones" ;

	/// Directory to which to write .srt files
	static final String subTitleStreamExtractDestinationDirectory = "D:\\temp"; //mkvInputDirectory ;

	/// Directory to which to write any .MKV files that have 6.1 or 7.1 sound, but without those streams
	static final String mkvWithoutHighEndAudioDestinationDirectory = "D:\\Temp" ;

	/// Set testMode to true to prevent mutations
	static boolean testMode = false ;

	/// Set to true to extract the subtitles from this file into one or more separate subtitle files
	static final boolean doSubTitleExtract = true ;

	/// Set to true to extract the 6.1/7.1 audio stream(s) from this file
	static final boolean doAudioStreamExtract = false ;

	/// File name to which to log activities for this application.
	static final String logFileName = "log_extract_pgs.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	static final String stopFileName = "C:\\Temp\\stop_pgs.txt" ;

	static final String pathToFFMPEG = "D:\\Program Files\\ffmpeg\\bin\\ffmpeg" ;
	static final String pathToFFPROBE = "D:\\Program Files\\ffmpeg\\bin\\ffprobe" ;
	static final String codecTypeSubTitleNameString = "subtitle" ;
	static final String codecNameSubTitlePGSString = "hdmv_pgs_subtitle" ;
	static final String codecNameSubTitleSRTString = "subrip" ;
	static final String codecTypeAudio = "audio" ;

	/// List of starting Strings for audio stream titles to be excluded from transcoding.
	static final String[] excludedAudioStreamTitleContains = {
			"6.1",
			"7.1"
	} ;
	
	/// List of allowable languages for audio streams
	static final String[] includedAudioStreamLanguageEquals = {
			"eng",
			"en" // I think the protocol is strictly three letters only, but just being cautious here.
	} ;
	
	/// Identify the allowable languages for subtitles.
	static final String[] allowableSubTitleLanguages = {
			"eng",
			"en"
	} ;
	
	/// These subtitle codec names to be extracted.
	/// This will mostly be used when selecting which streams to extract as separate files.
	static final String[] extractableSubTitleCodecNames = {
			codecNameSubTitlePGSString,
			codecNameSubTitleSRTString
	} ;

	/// The list of subtitle code names that should be left in any files to be transcoded directly
	static final String[] transcodeableSubTitleCodecNames = {
			codecNameSubTitleSRTString
	} ;
	
	public static void main(String[] args)
	{
		run_ffmpeg.testMode = testMode ;
		run_ffmpeg.openLogFile( logFileName ) ;

		// First, survey the input directory for files to process, and build
		// a TranscodeFile object for each.
		List< TranscodeFile > filesToProcess = run_ffmpeg.surveyInputDirectoryAndBuildTranscodeFiles( mkvInputDirectory ) ;

	  	// Perform the core work of this application: for each input file, create the appropriate directories,
	  	// process the file, and move the input and output files (if necessary).
		for( TranscodeFile theFileToProcess : filesToProcess )
		{
			if( run_ffmpeg.stopExecution() )
			{
				out( "main> Stopping execution due to presence of stop file: " + stopFileName ) ;
				break ;
			}

			// Look for usable subtitle streams in the file and retrieve a list of options
			// for an ffmpeg extract command
			FFmpegProbeResult probeResult = ffprobeFile( theFileToProcess ) ;
			
			if( doSubTitleExtract )
			{
				extractSubtitles( theFileToProcess, probeResult ) ;
			}
			
			if( doAudioStreamExtract )
			{
				extractAudioStreams( theFileToProcess, probeResult ) ;
			}
			
		} // for( fileToSubTitleExtract )
		out( "main> Exiting..." ) ;
		run_ffmpeg.closeLogFile() ;
	}

	public static FFmpegProbeResult ffprobeFile( TranscodeFile theFile )
	{
		log( "processFile> Processing: " + theFile.getMkvFileName() ) ;
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
		ffprobeExecuteCommand.add( "-i", theFile.getMKVFileNameWithPath() ) ;

		// Build the GSON parser for the JSON input
		GsonBuilder builder = new GsonBuilder(); 
		builder.setPrettyPrinting(); 

		Gson gson = builder.create();

		try
		{
			Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
			String ffprobeExecuteCommandString = run_ffmpeg.toStringForCommandExecution( ffprobeExecuteCommand.build() ) ;
			out( "processFile> Execute ffprobe command: " + ffprobeExecuteCommandString ) ;

			final Process process = Runtime.getRuntime().exec( ffprobeExecuteCommandString ) ;

			BufferedReader inputStreamReader = new BufferedReader( new InputStreamReader( process.getInputStream() ) ) ;
			int lineNumber = 1 ;
			String inputLine = null ;
			String inputBuffer = "" ;
			while( (inputLine = inputStreamReader.readLine()) != null )
			{
				log( "processFile:" + lineNumber + "> " + inputLine ) ;
				inputBuffer += inputLine ;
				++lineNumber ;
			}
			
			// Deserialize the JSON streams info from this file
			result = gson.fromJson(inputBuffer, FFmpegProbeResult.class);
		}
		catch( Exception theException )
		{
			theException.printStackTrace() ;
		}
		return result ;
	}

	static void extractSubtitles( TranscodeFile fileToSubTitleExtract, FFmpegProbeResult probeResult )
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
	
	static void extractAudioStreams( TranscodeFile fileToSubTitleExtract, FFmpegProbeResult probeResult )
	{
		// Build a set of options for an ffmpeg command based on the JSON input
		// If no suitable subtitles are found, the options string will be empty
		ImmutableList.Builder<String> audioExtractionOptionsString =
				buildFFmpegAudioExtractionOptionsString( probeResult ) ;
		
		if( audioExtractionOptionsString.build().isEmpty() )
		{
			// Nothing to do
			return ;
		}
		
		// Otherwise, found an MKV file that needs to have its 6.1/7.1 stream(s) removed
		ImmutableList.Builder<String> ffmpegCommandList = new ImmutableList.Builder< String >() ;
		ffmpegCommandList.add( pathToFFMPEG ) ;
		ffmpegCommandList.add( "-i", fileToSubTitleExtract.getMKVFileNameWithPath() ) ;
		ffmpegCommandList.add( "-map", "0" ) ;
		
		// audioExtractionOptionsString will include the streams to exclude of the form "-map -0:n"
		ffmpegCommandList.addAll( audioExtractionOptionsString.build() ) ;
//		ffmpegCommandList.add( run_ffmpeg.toStringForCommandExecution( audioExtractionOptionsString.build() ) ) ;
		ffmpegCommandList.add( "-c", "copy" ) ;
		
		String outputFileNameWithPath = run_ffmpeg.addPathSeparatorIfNecessary( mkvWithoutHighEndAudioDestinationDirectory )
				+ fileToSubTitleExtract.getMkvFileName() ;
		ffmpegCommandList.add( outputFileNameWithPath ) ;
		
		run_ffmpeg.executeCommand( ffmpegCommandList ) ;
	}
	
	static ImmutableList.Builder<String> buildFFmpegSubTitleExtractionOptionsString( FFmpegProbeResult probeResult,
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
			String outputFileNameWithPath = run_ffmpeg.addPathSeparatorIfNecessary( subTitleStreamExtractDestinationDirectory )
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
		log( "buildFFmpegSubTitleExtractionOptionsString> ffmpegOptionsCommandString: "
				+ run_ffmpeg.toStringForCommandExecution( ffmpegOptionsCommandString.build() ) ) ;
		return ffmpegOptionsCommandString ;	
	}

	/**
	 * Remove the 6.1/7.1 and non-allowable language audio streams via ffmpeg mapping options and keep everything else.
	 * @param probeResult
	 * @param theFile
	 * @return
	 */
	static ImmutableList.Builder<String> buildFFmpegAudioExtractionOptionsString( FFmpegProbeResult probeResult )
	{
		// The ffmpegOptionsCommandString will contain only the options to extract audio streams
		// from the given FFmpegProbeResult
		// All of the actual ffmpeg command build ("ffmpeg -i ...") happens elsewhere
		ImmutableList.Builder<String> ffmpegOptionsCommandString = new ImmutableList.Builder<String>();

		List< Integer > streamsToExclude = findExcludedAudioStreamsAsInteger( probeResult.streams ) ;
		if( streamsToExclude.isEmpty() )
		{
			// Return an empty command list
			return new ImmutableList.Builder<String>() ;
		}

		// At least one stream needs to be removed
		for( Integer theInteger : streamsToExclude )
		{
			ffmpegOptionsCommandString.add( "-map", "-0:" + theInteger ) ;
		}
		
		log( "buildFFmpegAudioExtractionOptionsString> ffmpegOptionsCommandString: "
				+ run_ffmpeg.toStringForCommandExecution( ffmpegOptionsCommandString.build() ) ) ;
		return ffmpegOptionsCommandString ;	
	}
	
	/**
	 * Walk through the list of streams, find and return the subtitle streams that can be extracted.
	 * These are generally the PGS and SRT streams, and in English only.
	 * Will always return a non-null list, although it may be empty.
	 * @param probeResult
	 * @return
	 */
	static List< FFmpegStream > findExtractableSubTitleStreams( FFmpegProbeResult probeResult )
	{
		List< FFmpegStream > extractableSubTitleStreams = new ArrayList< FFmpegStream >() ;
		
		for( FFmpegStream theStream : probeResult.streams )
		{
			log( "findExtractableSubTitleStreams> Checking stream: " + theStream ) ;
			if( !theStream.codec_type.equals( codecTypeSubTitleNameString ) )
			{
				// Not a subtitle stream
				log( "findIncludedSubTitleStreams> Ignoring stream: " + theStream ) ;
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
					log( "findExtractableSubTitleStreams> Found subtitle with language tag but NOT allowable: "
							+ theStream.tags.get( "language" ) ) ;
					continue ;
				}
			}
			// Post condition: this is a subtitle stream with an allowable language

			if( isExtractableSubTitleCodecName( theStream.codec_name ) )
			{
				// Found allowable subtitle type
				log( "findExtractableSubTitleStreams> Found allowable subtitle stream: " + theStream ) ;
				extractableSubTitleStreams.add( theStream ) ;
			}
		} // for( stream )
		return extractableSubTitleStreams ;
	}
	
	static boolean isTranscodeableSubTitleCodecName( final String stCodeName )
	{
		for( String allowableCodecName : transcodeableSubTitleCodecNames )
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
	
	static boolean isAllowableAudioLanguage( final String audioLanguage )
	{
		for( String allowableLanguage : includedAudioStreamLanguageEquals )
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
	
	static boolean isExcludedAudioTitle( final String audioTitle )
	{
		for( String excludedTitle : excludedAudioStreamTitleContains )
		{
			if( audioTitle.contains( excludedTitle ) )
			{
				// Found an allowable true
				return true ;
			}
		}
		// No allowable language found
		return false ;
	}
	
	/**
	 * Search for excluded streams in the list of FFmpegStreams.
	 * Always returns a non-null List, but it may be empty.
	 * @param inputStreams
	 * @return
	 */
	static List< FFmpegStream > findExcludedAudioStreams( final List< FFmpegStream > inputStreams )
	{
		List< FFmpegStream > excludedAudioStreams = new ArrayList< FFmpegStream >() ;
		
		// Walk through the list of streams and add the index corresponding to any 6.1 or 7.1
		// streams to the streamsToExclude list
		for( FFmpegStream theStream : inputStreams )
		{
//			log( "findExcludedAudioStreams> stream: " + theStream ) ;
			if( !theStream.codec_type.equalsIgnoreCase( codecTypeAudio ) )
			{
				// Not an audio stream
				// Ignore it (will be included in the output)
				continue ;
			}
			// Post condition: This is an audio stream
			if( !theStream.tags.containsKey( "title" ) )
			{
				out( "findExcludedAudioStreams> Missing \"title\" for audio stream "
						+ theStream ) ;
				continue ;
			}
			// Post condition: the audio stream has a tag named "title"

			// Search this audio stream for a title that contains any of the excluded audio stream titles
			final String titleValueString = theStream.tags.get( "title" ) ;
			if( isExcludedAudioTitle( titleValueString ) )
			{
				// This stream title starts with the excluded audio stream title
				log( "findExcludedAudioStreams> Found excluded audio title stream: " + theStream ) ;

				// Record this stream as being excluded
				excludedAudioStreams.add( theStream ) ;
			}
			
			if( !theStream.tags.containsKey( "language" ) )
			{
				out( "findExcludedAudioStreams> Missing \"language\" for audio stream: "
						+ theStream ) ;
				continue ;
			}
			
			final String languageValueString = theStream.tags.get( "language" ) ;
			if( !isAllowableAudioLanguage( languageValueString ) )
			{
				log( "findExcludedAudioStreams> Audio language not allowed for audio stream: "
						+ theStream ) ;
				// Record this stream as being excluded
				excludedAudioStreams.add( theStream ) ;
			}
			
		} // for( theStream )
		return excludedAudioStreams ;
	}
	
	static List< Integer > findExcludedAudioStreamsAsInteger( final List< FFmpegStream > inputStreams )
	{
		List< FFmpegStream > excludedAudioStreams = findExcludedAudioStreams( inputStreams ) ;
		List< Integer > excludedAudioStreamsAsInteger = new ArrayList< Integer >() ;
		for( FFmpegStream theStream : excludedAudioStreams )
		{
			Integer streamIndex = Integer.valueOf( theStream.index ) ;
			excludedAudioStreamsAsInteger.add( streamIndex ) ;
		}
		return excludedAudioStreamsAsInteger ;
	}
	
	static void out( final String outputMe )
	{
		run_ffmpeg.out( outputMe ) ;
	}
	
	static void log( final String logMe )
	{
		run_ffmpeg.log( logMe ) ;
	}
	

}
