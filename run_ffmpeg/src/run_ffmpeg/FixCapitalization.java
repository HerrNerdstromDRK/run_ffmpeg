package run_ffmpeg;

import java.io.File ;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.text.WordUtils;

/**
 * Walks through all of the files given below and fixes capitalization.
 * That is, it will set the first letter of each word in the file name as capital.
 */
public class FixCapitalization
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_fix_capitalization.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	//	private static final String stopFileName = "C:\\Temp\\stop_fix_capitalization.txt" ;

	public FixCapitalization()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		FixCapitalization fc = new FixCapitalization() ;
		fc.execute() ;
	}

	public void execute()
	{
		common.setTestMode( true ) ;

		final List< String > directories = new ArrayList< String >() ;
		directories.add( Common.getPathToToOCR() ) ;

		// Look through each directory for all files ending in .mkv, .mp4, or .srt
		capitalizeFiles( directories, new String[] { ".mkv", ".mp4", ".srt" } ) ;
	}

	public int capitalizeFile( File inputFile )
	{
		assert( inputFile != null ) ;

		// Tokenize the filename
		final Pattern fileNamePattern = Pattern.compile( "(?<showName>.*) - S(?<seasonNumber>[\\d]+)E(?<episodeNumber>[\\d]+) - (?<episodeName>.*)(?<extensionString>(?:\\.en)\\..*)" ) ;
		final Matcher fileNameMatcher = fileNamePattern.matcher( inputFile.getName() ) ;

		if( !fileNameMatcher.find() )
		{
			log.warning( "Invalid filename: " + inputFile.getName() ) ;
			return 0 ;
		}

		final String showName = fileNameMatcher.group( "showName" ) ;
		final String seasonNumberString = fileNameMatcher.group( "seasonNumber" ) ;
		final String episodeNumberString = fileNameMatcher.group( "episodeNumber" ) ;
		final String episodeName = fileNameMatcher.group( "episodeName" ) ;
		final String extensionString = fileNameMatcher.group( "extensionString" ) ;

		final String newShowName = WordUtils.capitalizeFully( showName ) ;
		final String newEpisodeName = WordUtils.capitalizeFully( episodeName ) ;

		if( showName.equals( newShowName ) && episodeName.equals( newEpisodeName ) )
		{
			// No changes.
			return 0 ;
		}
		// PC: Capitalization has changed the file name, so need to build new file name and rename the file.

		final String newFileName = newShowName
				+ " - "
				+ "S" + seasonNumberString
				+ "E" + episodeNumberString
				+ " - " + episodeName
				+ extensionString ;
		final File newFile = new File( inputFile.getParentFile(), newFileName ) ;

		// Since Windows will reject a rename based on capitalization alone, rename the inputFile to a temp file
		// then back to the original destination.
		final File tmpDirFile = new File( Common.getPathToTmpDir() ) ;
		final File tmpFile = new File( tmpDirFile, newFileName ) ;

		log.info( "Renaming: " + inputFile.getAbsolutePath() + " -> " + newFile.getAbsolutePath() ) ;
		if( !common.getTestMode() )
		{
			inputFile.renameTo( tmpFile ) ;
			tmpFile.renameTo( newFile ) ;
		} // if( testMode )

		return 1 ;
	} // capitalizeFile()

	public void capitalizeFiles( final List< String > directories, final String[] extensions )
	{
		assert( directories != null ) ;
		assert( extensions != null ) ;

		int numRenamedFiles = 0 ;
		List< File > files = new ArrayList< File >() ;

		for( String directory : directories )
		{
			files.addAll( common.getFilesInDirectoryByExtension( directory, extensions ) ) ;
		}
		// PC: Have all files with the given extensions in the list of directories.

		for( File theFile : files )
		{
			numRenamedFiles += capitalizeFile( theFile ) ;
		}
		log.info( "Renamed " + numRenamedFiles + " file(s)" ) ;
	} // capitalizeFiles()
}  // class {}
