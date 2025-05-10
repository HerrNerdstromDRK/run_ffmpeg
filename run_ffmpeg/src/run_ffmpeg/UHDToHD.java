package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

/**
 * The purpose of this class is to convert individual files from 4K to 1080p.
 */
public class UHDToHD
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_compare_transcode_options.txt" ;
	
	public UHDToHD()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}
	
	public static void main( String[] args )
	{
		(new UHDToHD()).execute() ;
	}
	
	public void execute()
	{
		common.setTestMode( false ) ;
		
		// Convert individual files from 4K to 1080p
		// Places converted files in the tmpDir
		List< String > filePaths = new ArrayList< String >() ;
		filePaths.add( "\\\\skywalker\\Media\\Movies\\Schindlers List (1993) {edition-4K}\\Schindlers List (1993) {edition-4K}.mkv" ) ;
		
		for( String filePath : filePaths )
		{
			downConvertTo1080p( filePath ) ;
		}
	}

	public void downConvertTo1080p( final String inputFilePath )
	{
		final File inputFile = new File( inputFilePath ) ;
		assert( inputFile.exists() ) ;
		assert( inputFile.isFile() ) ;
		
		// Build output file name
		final String outputFileNameWithPath = common.addPathSeparatorIfNecessary( Common.getPathToTmpDir() )
				+ inputFile.getName() ;
		
		ImmutableList.Builder< String > ffmpegOptionsCommandString = new ImmutableList.Builder< String >() ;
		ffmpegOptionsCommandString.add( common.getPathToFFmpeg() ) ;
		ffmpegOptionsCommandString.add( "-y" ) ;
		ffmpegOptionsCommandString.add( "-analyzeduration", Common.getAnalyzeDurationString() ) ;
		ffmpegOptionsCommandString.add( "-probesize", Common.getProbeSizeString() ) ;
		ffmpegOptionsCommandString.add( "-i", inputFilePath ) ;
		ffmpegOptionsCommandString.add( "-filter:v", "scale=width=1920:height=-2" ) ;
		ffmpegOptionsCommandString.add( "-c:a", "copy" ) ;
		ffmpegOptionsCommandString.add( "-c:s", "copy" ) ;
//		ffmpegOptionsCommandString.add( "-ss", "00:05:00.0" ) ;
//		ffmpegOptionsCommandString.add( "-t", "30" ) ;
		ffmpegOptionsCommandString.add( outputFileNameWithPath ) ;
		
		boolean executeSuccess = common.executeCommand( ffmpegOptionsCommandString ) ;
		if( !executeSuccess )
		{
			log.warning( "Failed to down convert: " + inputFilePath.toString() ) ;
		}
	}	
}
