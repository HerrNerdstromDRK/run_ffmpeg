package run_ffmpeg;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

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

	protected static String[] variable_presets =
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

	protected static String[] variable_crf =
		{
				"0",
				"5",
				"10",
				"15",
				"20",
				"25"
		} ;

	protected static String[] inputFilesPaths =
		{
//				"\\\\skywalker\\Media\\TV_Shows\\Rick And Morty (2013) {tvdb-275274}\\Season 03\\Rick And Morty - S03E01 - The Rickshank Redemption.mkv", // h264 HD
				"\\\\skywalker\\Media\\TV_Shows\\Planet Earth (2006) {tvdb-79257}\\Season 01\\Planet Earth - S01E01 - From Pole To Pole.mkv", // vc1 HD
//				"\\\\\\skywalker\\Media\\TV_Shows\\The Simpsons (1989) {tvdb-71663}\\Season 01\\The Simpsons - S01E01 - Simpsons Roasting On The Open Fire.mkv" // mp2 SD
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
		common.setTestMode( false ) ;
		
		try
		{
			// File format: source codec,dest codec,preset,crf,output file name,input file size,output file size,transcode time (ms),time per GB
			BufferedWriter dataFileWriter = new BufferedWriter( new FileWriter( dataFileName ) ) ;
			testAll( dataFileWriter ) ;

//			final File rickshankFile = new File( inputFilesPaths[ 0 ] ) ;
//			FFmpegProbeResult fickshankProbeResult = common.ffprobeFile( rickshankFile, log ) ;
//			
//			testTranscode( rickshankFile,
//					dataFileWriter,
//					fickshankProbeResult,
//					"libx264",
//					"ultrafast",
//					"25" ) ;
			dataFileWriter.close() ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}
	
	public void testAll( BufferedWriter dataFileWriter )
	{
		try
		{
			for( String inputFilePath : inputFilesPaths )
			{
				final File inputFile = new File( inputFilePath ) ;
				assert( inputFile.exists() ) ;

				FFmpegProbeResult inputFileProbeResult = common.ffprobeFile( inputFile, log ) ;
				
				if( inputFileProbeResult.isH265() )
				{
					// Already H265 -- nothing to transcode
					continue ;
				}

				for( String preset : variable_presets )
				{
					for( String crf : variable_crf )
					{
						if( !inputFileProbeResult.isH264() )
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
						testTranscode( inputFile,
								dataFileWriter,
								inputFileProbeResult,
								"libx265",
								preset,
								crf ) ;
					}
				}
			}
		} // try
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}

	public boolean testTranscode( final File inputFile,
			BufferedWriter dataFileWriter,
			final FFmpegProbeResult inputFileProbeResult,
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
			String outputFileNameWithPath = common.addPathSeparatorIfNecessary( Common.getPathToTmpDir() )
					+ Common.stripExtensionFromFileName( inputFile.getName() )
					+ "_" + inputFileProbeResult.getVideoCodec()
					+ "_to_" + videoCodec
					+ "_preset_" + preset
					+ "_crf_" + crf
					+ "_starttime_" + System.currentTimeMillis()
					+ "." + Common.getExtension( inputFile.getName() ) ;							
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
			// File format: source codec,dest codec,preset,crf,output file name,input file size,output file size,transcode time (ms),time per GB
			String transcodeDataLine = inputFileProbeResult.getVideoCodec() + "," ;
			transcodeDataLine += videoCodec + "," ;
			transcodeDataLine += preset + "," ;
			transcodeDataLine += crf + "," ;
			transcodeDataLine += outputFileNameWithPath + "," ;
			transcodeDataLine += inputFile.length() + "," ;
			final File outputFile = new File( outputFileNameWithPath ) ;
			transcodeDataLine += outputFile.length() + "," ;
			transcodeDataLine += timeElapsedInSeconds + "," ;
			transcodeDataLine += timePerGigaByte ;
			
			dataFileWriter.write( transcodeDataLine + System.lineSeparator() ) ;
			dataFileWriter.flush() ;
		} // try
		catch( Exception theException )
		{
			log.warning( "Error with file " + dataFileName + ": " + theException.toString() ) ;
		}
		
		return true ;
	} // testTranscode

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
