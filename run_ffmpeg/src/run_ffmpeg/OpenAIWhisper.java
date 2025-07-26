package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

public class OpenAIWhisper
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_openaiwhisper.txt" ;
	
	public OpenAIWhisper()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public OpenAIWhisper( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;
	}
	
	public static void main( String[] args )
	{
		(new OpenAIWhisper()).execute() ;
	}
	
	public void execute()
	{
		common.setTestMode( false ) ;
		final String wavFileNameWithPath = "\\\\skywalker\\Media\\To_OCR\\Boardwalk Empire (2010) {imdb-0979432}\\Season 02\\BOARDWALK_EMPIRE_S2_DISC1-F3_t01.wav" ;
 
		transcribeToSRT( wavFileNameWithPath ) ;
	}
	
	/**
	 * Transcribe a .wav file to .srt.
	 * @param inputFile
	 * @return
	 */
	public File transcribeToSRT( final String intputFileWithPath )
	{
		final File wavFile = new File( intputFileWithPath ) ;
		return transcribeToSRT( wavFile ) ;
	}
	
	/**
	 * Transcribe a .wav file to .srt. Places the srt in the source directory.
	 * @param inputFile
	 * @return
	 */
	public File transcribeToSRT( final File inputFile )
	{
		final String outputFileNameWithPath = inputFile.getAbsolutePath().replace( ".wav", ".srt" ) ;
		final File outputFile = new File( outputFileNameWithPath ) ;
		
		if( outputFile.exists() )
		{
			log.info( "Output file " + outputFile.getAbsolutePath() + " already exists" ) ;
			return null ;
		}
		
		ImmutableList.Builder< String > whisperCommand = new ImmutableList.Builder< String >() ;

		// Setup ffmpeg basic options
		whisperCommand.add( "whisper" ) ;
		whisperCommand.add( "--language", "English" ) ;
		whisperCommand.add( "--output_format", "srt" ) ;
		whisperCommand.add( "--output_dir", outputFile.getParent() ) ;
		whisperCommand.add( "--fp16", "False" ) ;
		whisperCommand.add( inputFile.getAbsolutePath() ) ;

		log.info( common.toStringForCommandExecution( whisperCommand.build() ) ) ;

		// Only execute the whisper if testMode is false
		boolean executeSuccess = common.getTestMode() ? true : common.executeCommand( whisperCommand ) ;
		return executeSuccess ? outputFile : null ;
	}
}
