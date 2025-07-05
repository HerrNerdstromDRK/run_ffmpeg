package run_ffmpeg;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

/**
 * Iterate through multiple sets of options for transcoding to compare
 *  speed, size, and quality of the transcode and resulting output.
 */
public class CompareTranscodeOptions
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_compare_transcode_options.txt" ;
	private static final String dataFileName = "log_compare_transcode_options_data.txt" ;

	protected final static String[] variable_presets =
		{
				"ultrafast",
				"superfast",
				"veryfast",
				"faster",
				"fast",
				"medium", // default
				"slow",
				"slower",
				"veryslow"
				//		"placebo"
		} ;

	protected final static String[] variable_crf =
		{
				"0",
				"5",
				"10",
				"15",
				"20",
				"25"
		} ;

	protected final static String[] variable_output_codecs =
		{
				"h264",
				"h265"
		} ;

	protected final static String[] inputFilesPaths =
		{
				"\\\\skywalker\\Media\\TV_Shows\\Rick And Morty (2013) {tvdb-275274}\\Season 03\\Rick And Morty - S03E01 - The Rickshank Redemption.mkv", // h264 HD
				"\\\\skywalker\\Media\\TV_Shows\\Planet Earth (2006) {tvdb-79257}\\Season 01\\Planet Earth - S01E01 - From Pole To Pole.mkv", // vc1 HD
				"\\\\\\skywalker\\Media\\TV_Shows\\The Simpsons (1989) {tvdb-71663}\\Season 01\\The Simpsons - S01E01 - Simpsons Roasting On The Open Fire.mkv" // mp2 SD
		} ;

	public CompareTranscodeOptions()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		(new CompareTranscodeOptions()).execute() ;
	}

	public void execute()
	{
		common.setTestMode( true ) ;

		final File outputDirectory = new File( Common.getPathToTmpDir() ) ;
		final File[] filesInOutputDirectory = outputDirectory.listFiles() ;
		assert( filesInOutputDirectory != null ) ;
		
		try
		{
			// File format: source codec,dest codec,preset,crf,output file name,input file size,output file size,transcode time (s),time per GB
			BufferedWriter dataFileWriter = new BufferedWriter( new FileWriter( dataFileName, true ) ) ;
			testAll( dataFileWriter, filesInOutputDirectory ) ;
			//determineTranscodeDurations( dataFileWriter ) ;

			//			final File rickshankFile = new File( inputFilesPaths[ 0 ] ) ;
			//			FFmpegProbeResult fickshankProbeResult = common.ffprobeFile( rickshankFile, log ) ;
			//			
			//			testTranscode( rickshankFile,
			//					dataFileWriter,
			//					fickshankProbeResult,
			//					"libx264",
			//					"ultrafast",
			//					"25" ) ;
			dataFileWriter.flush() ;
			dataFileWriter.close() ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}

	public void testAll( BufferedWriter dataFileWriter, final File[] filesInOutputDirectory )
	{
		try
		{
			for( String inputFilePath : inputFilesPaths )
			{
				final File inputFile = new File( inputFilePath ) ;
				assert( inputFile.exists() ) ;

				FFmpeg_ProbeResult inputFileProbeResult = common.ffprobeFile( inputFile, log ) ;

				if( inputFileProbeResult.isH265() )
				{
					// Already H265 -- nothing to transcode
					continue ;
				}

				for( String preset : variable_presets )
				{
					for( String crf : variable_crf )
					{
						if( !inputFileProbeResult.isH264() && !testComplete( filesInOutputDirectory, inputFile, inputFileProbeResult, "libx264", preset, crf ) )
						{
							// Upgrade to H264
							testTranscode( inputFile,
									dataFileWriter,
									inputFileProbeResult,
									"libx264",
									preset,
									crf ) ;							
						}
						// Always upgrade to H265
						if( !testComplete( filesInOutputDirectory, inputFile, inputFileProbeResult, "libx265", preset, crf ) )
							{
							testTranscode( inputFile,
								dataFileWriter,
								inputFileProbeResult,
								"libx265",
								preset,
								crf ) ;
							}
					} // for( crf )
				} // for( preset )
			} // for( inputFilePath )
		} // try
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}

	/**
	 * Return true if an output file (new or old name format) exists for the given set of parameters, false otherwise.
	 * @return
	 */
	public boolean testComplete( final File[] filesInOutputDirectory,
			final File inputFile,
			final FFmpeg_ProbeResult inputFileProbeResult,
			final String videoCodec,
			final String preset,
			final String crf )
	{
		final File outputFileWithOldName = new File( makeOldOutputFileName( inputFile, inputFileProbeResult, videoCodec, preset, crf) ) ;
		if( outputFileWithOldName.exists() )
		{
//			log.info( "Found file with old name: " + outputFileWithOldName.getAbsolutePath() ) ;
			return true ;
		}
		// PC: File not built with old file name

		final String outputFileNameWithNewName = makeOutputFileName( inputFile, inputFileProbeResult, videoCodec, preset, crf ) ;
		final File outputFileWithNewName = new File( outputFileNameWithNewName ) ;

		// The challenge with the new format is that the file name includes the start time in milliseconds since the epoch.
		// Need to scan all files in the current folder matching against the file pattern.
		// Be sure to avoid the path as all of the \'s confuse things and the Pattern.quote() method is buggy.
		final String outputFileNameWithNewNamePatternString = outputFileWithNewName.getName().replaceAll( "_[\\d]+\\.", "_[\\\\d]+\\\\." ) ;
//		log.info( "outputFileNameWithNewNamePatternString: " + outputFileNameWithNewNamePatternString ) ;

		// Search the list of files in the output directory for a match.
		Pattern outputFileNameWithNewNamePattern = Pattern.compile( outputFileNameWithNewNamePatternString ) ;
		for( File testFile : filesInOutputDirectory )
		{
			final Matcher outputFileNameWithNewNameMatcher = outputFileNameWithNewNamePattern.matcher( testFile.getName() ) ;
			if( outputFileNameWithNewNameMatcher.find() )
			{
//				log.info( "Found match for " + outputFileNameWithNewName + " with " + testFile.getAbsolutePath() ) ;
				return true ;
			}
		}
		log.info( "No match for " + outputFileNameWithNewName ) ;
		return false ;
	}

	public boolean testTranscode( final File inputFile,
			BufferedWriter dataFileWriter,
			final FFmpeg_ProbeResult inputFileProbeResult,
			final String videoCodec,
			final String preset,
			final String crf )
	{
		try
		{
			// Build the ffmpeg command
			// ffmpegCommand will hold the command to execute ffmpeg
			ImmutableList.Builder< String > ffmpegCommand = new ImmutableList.Builder<String>() ;

			// Setup ffmpeg basic options
			ffmpegCommand.add( common.getPathToFFmpeg() ) ;

			// Overwrite existing files
			ffmpegCommand.add( "-y" ) ;

			// Not exactly sure what these do but it seems to help reduce errors on some files.
			ffmpegCommand.add( "-analyzeduration", Common.getAnalyzeDurationString() ) ;
			ffmpegCommand.add( "-probesize", Common.getProbeSizeString() ) ;

			// Include source file
			ffmpegCommand.add( "-i", inputFile.getAbsolutePath() ) ;

			ffmpegCommand.add( "-c:v", videoCodec ) ;
			ffmpegCommand.add( "-preset", preset ) ;
			ffmpegCommand.add( "-crf", crf) ;
			//					ffmpegCommand.add( "-tag:v", "hvc1" ) ;
			ffmpegCommand.add( "-movflags", "+faststart" ) ;
			ffmpegCommand.add( "-metadata", "title=" + getTitle( inputFile ) ) ;

			// Copy audio and subtitles
			//					ffmpegCommand.add( "-map", "0:a" ) ;
			ffmpegCommand.add( "-c:a", "copy" ) ;
			//					ffmpegCommand.add( "-map", "0:s" ) ;
			ffmpegCommand.add( "-c:s", "copy" ) ;

			// Add output filename
			String outputFileNameWithPath = makeOutputFileName( inputFile,
					inputFileProbeResult,
					videoCodec,
					preset,
					crf ) ;

			//					log.info( "outputFileNameWithPath: " + outputFileNameWithPath ) ;
			ffmpegCommand.add( outputFileNameWithPath ) ;

			long startTime = System.nanoTime() ;
			log.info( common.toStringForCommandExecution( ffmpegCommand.build() ) ) ;

			// Only execute the transcode if testMode is false
			boolean executeSuccess = common.executeCommand( ffmpegCommand ) ;
			if( !executeSuccess )
			{
				log.warning( "Error in execute command" ) ;
				// Do not move any files since the transcode failed
				return false ;
			}

			long endTime = System.nanoTime() ; double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;

			double timePerGigaByte = timeElapsedInSeconds / (inputFile.length() / 1000000000.0) ;
			log.info( "Elapsed time to transcode "
					+ inputFile.getAbsolutePath()
					+ ": "
					+ common.getNumberFormat().format( timeElapsedInSeconds )
					+ " seconds, "
					+ common.getNumberFormat().format( timeElapsedInSeconds / 60.0 )
					+ " minutes, or "
					+ common.getNumberFormat().format( timePerGigaByte )
					+ " seconds per GB" ) ;

			// Write data to file
			// File format: source codec,dest codec,preset,crf,output file name,input file size,output file size,transcode time (s),time per GB
			final File outputFile = new File( outputFileNameWithPath ) ;

			String transcodeDataLine = inputFileProbeResult.getVideoCodec() + "," ;
			transcodeDataLine += videoCodec + "," ;
			transcodeDataLine += preset + "," ;
			transcodeDataLine += crf + "," ;
			transcodeDataLine += outputFileNameWithPath + "," ;
			transcodeDataLine += inputFile.length() + "," ;
			transcodeDataLine += outputFile.length() + "," ;
			transcodeDataLine += timeElapsedInSeconds + "," ;
			transcodeDataLine += timePerGigaByte ;

			if( common.getTestMode() )
			{
				log.info( "dataFileWriter.write: " + transcodeDataLine ) ;
			}
			else
			{
				dataFileWriter.write( transcodeDataLine + System.lineSeparator() ) ;
				dataFileWriter.flush() ;
			}
		} // try
		catch( Exception theException )
		{
			log.warning( "Error with file " + dataFileName + ": " + theException.toString() ) ;
		}

		return true ;
	} // testTranscode

	/**
	 * Return the output filename given the input file name and transcode parameters.
	 * @param inputFile
	 * @param inputFileProbeResult
	 * @param videoCodec
	 * @param preset
	 * @param crf
	 * @return
	 */
	public String makeOutputFileName( final File inputFile,
			final FFmpeg_ProbeResult inputFileProbeResult,
			final String videoCodec,
			final String preset,
			final String crf )
	{
		final String fileName = common.addPathSeparatorIfNecessary( Common.getPathToTmpDir() )
				+ Common.stripExtensionFromFileName( inputFile.getName() )
				+ "_" + inputFileProbeResult.getVideoCodec()
				+ "_to_" + videoCodec
				+ "_preset_" + preset
				+ "_crf_" + crf
				+ "_starttime_" + System.currentTimeMillis()
				+ "." + Common.getExtension( inputFile.getName() ) ;
		return fileName ;
	}

	/**
	 * Return the output filename, in the old format, for the given input file and transcode parameters. 
	 * @param inputFile
	 * @param inputFileProbeResult
	 * @param videoCodec
	 * @param preset
	 * @param crf
	 * @return
	 */
	public String makeOldOutputFileName( final File inputFile,
			final FFmpeg_ProbeResult inputFileProbeResult,
			final String videoCodec,
			final String preset,
			final String crf )
	{
		final String fileName = common.addPathSeparatorIfNecessary( Common.getPathToTmpDir() )
				+ Common.stripExtensionFromFileName( inputFile.getName() )
				+ "_" + inputFileProbeResult.getVideoCodec()
				+ "_to_" + videoCodec.replace( "libx", "h" )
				+ "_preset_" + preset
				+ "_crf_" + crf
				+ "." + Common.getExtension( inputFile.getName() ) ;
		return fileName ;
	}

	public void determineTranscodeDurations( BufferedWriter dataFileWriter )
	{
		long previousStartTime = 0 ;

		// Walk through each file.
		// They should be in the same order as transcoded, mostly.
		for( String inputFilePath : inputFilesPaths )
		{
			final File inputFile = new File( inputFilePath ) ;
			final FFmpeg_ProbeResult inputFileProbeResult = common.ffprobeFile( inputFile, log ) ;

			if( inputFileProbeResult.isH265() )
			{
				// Skip this file.
				continue ;
			}

			for( String preset : variable_presets )
			{
				for( String crf : variable_crf )
				{
					for( String outputCodec : variable_output_codecs )
					{
						final String inputVideoCodec = inputFileProbeResult.getVideoCodec() ;
						if( outputCodec.equals( inputVideoCodec ) )
						{
							// Input file is already in the targeted ouput video protocol.
							// Skip this codec output.
							continue ;
						}
						// PC: Input codec is different from target codec.

						try
						{
							// Should have been converted to H264
							final String outputFileNameWithPath = makeOutputFileName( inputFile, inputFileProbeResult, outputCodec, preset, crf ) ;
							final File outputFile = new File( outputFileNameWithPath ) ;

							// File format: source codec,dest codec,preset,crf,output file name,input file size,output file size,transcode time (s),time per GB
							String transcodeDataLine = inputFileProbeResult.getVideoCodec() + "," ;
							transcodeDataLine += outputCodec + "," ;
							transcodeDataLine += preset + "," ;
							transcodeDataLine += crf + "," ;
							transcodeDataLine += outputFileNameWithPath + "," ;
							transcodeDataLine += inputFile.length() + "," ;
							transcodeDataLine += outputFile.length() + "," ;

							long timeElapsedInSeconds = Math.abs( outputFile.lastModified() - previousStartTime ) / 1000 ;
							if( 0 == timeElapsedInSeconds )
							{
								timeElapsedInSeconds = Long.MAX_VALUE ;
							}
							transcodeDataLine += timeElapsedInSeconds + "," ;
							transcodeDataLine += inputFile.length() / timeElapsedInSeconds ;

							dataFileWriter.write( transcodeDataLine + System.lineSeparator()) ;
							dataFileWriter.flush() ;							

							previousStartTime = outputFile.lastModified() ;
						} // try
						catch( Exception theException )
						{
							log.warning( "Exception: " + theException.toString() ) ;
						}
					} // for( outputCodec )
				} // for( crf )
			} // for( preset )
		} // for( inputFilePath )
	} // determineTranscodeDuration()

	public String getTitle( final File inputFile )
	{
		String title = "Unknown" ;
		Path thePath = Paths.get( inputFile.getAbsolutePath() ) ;

		if( inputFile.getAbsolutePath().contains( "Season " ) && !inputFile.getAbsolutePath().contains( "Season (" ) )
		{
			// TV Show
			final String tvShowEpisode = thePath.getFileName().toString() ;

			// Tokenize
			final String[] tokens = tvShowEpisode.split( " - " ) ;
			for( String token : tokens )
			{
				log.fine( "Token: " + token ) ;
			}
			if( tokens.length != 3 )
			{
				log.warning( "Invalid number of tokens (" + tokens.length + ") for file: " + inputFile.getAbsolutePath() ) ;
				return title ;
			}
			// Exactly three tokens in tvShowEpisode
			final String episodeName = tokens[ 2 ].substring( 0, tokens[ 2 ].length() - 4 ) ;
			log.fine( "episodeName: " + episodeName ) ;

			title = episodeName ;			
		}
		else
		{
			// Movie
			final String fileNameWithoutExtension = thePath.getFileName().toString().substring( 0, thePath.getFileName().toString().length() - 4 ) ;

			// Files in movie directories are of the form:
			// "Movie (2000)" or
			// "Making Of-behindthescenes"
			String fileName = fileNameWithoutExtension ;
			if( fileName.contains( "(" ) )
			{
				// String the year string (" (2000)")
				fileName = fileName.substring( 0, fileName.length() - 7 ) ;
			}
			else if( fileName.contains ( "-" ) )
			{
				final String[] tokens = fileName.split( "-" ) ;
				fileName = tokens[ 0 ] ;
			}
			else
			{
				log.warning( "Parse error on fileName: " + fileName ) ;
			}
		}		
		return title ;
	} // getTitle()	
}
