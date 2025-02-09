package run_ffmpeg;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

public class RenameSubtitleFiles
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_rename_subtitle_files.txt" ;
	
	public RenameSubtitleFiles()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}
	
	public static void main( String[] args )
	{
		(new RenameSubtitleFiles()).execute() ;
	}
	
	public void execute()
	{
		final String pathToMovies = Common.getPathToMovies() ;
		final String pathToTVShows = Common.getPathToTVShows() ;

		execute( pathToMovies ) ;
		execute( pathToTVShows ) ;
	}
	
	public void execute( final String directory )
	{
		int properlyFormedNames = 0 ;
		List< File > srtFiles = common.getFilesInDirectoryByExtension( directory,  "srt" ) ;
		log.info( "Found " + srtFiles.size() + " srt file(s) at " + directory ) ;
		
//		Set< String > fileNamesWithoutStreamNumberOrExtension = new HashSet< String >() ;
		
		// Need to 
		for( File srtFile : srtFiles )
		{
			final String fileName = srtFile.getName() ;
			
			if( fileName.contains(".en." ) || fileName.contains( ".forced." ))
			{
				// Already properly named?
				++properlyFormedNames ;
				log.fine( "Skipping properly named file: " + srtFile.getAbsolutePath() ) ;
				continue ;
			}
			
			// File conversion should be of the form:
			//  - Movie: Thor The Dark World (2010).1.srt -> Thor The Dark World (2010).en.1.srt
			//  - Movie: Thor The Dark World (2010).2.srt -> Thor The Dark World (2010).en.2.srt
			//  - Movie: Thor The Dark World (2010).forced_subtitle.2.srt -> Thor The Dark World (2010).en.forced.srt
			//  - Movie extra: Making Of-behindthescenes.1.srt -> Making Of-behindthescenes.en.1.srt
			//  - TV: Game Of Thrones - S03E06 - The Climb.13.srt -> Game Of Thrones - S03E06 - The Climb.en.13.srt
			//  - TV: Game Of Thrones - S03E06 - The Climb.forced_subtitle.14.srt -> Game Of Thrones - S03E06 - The Climb.en.forced.srt
			//  Any video may have more than one non-forced subtitle files
			
			// Need to find srt files that are duplicates of each other with different stream numbers
			// Do so by stripping off the extension and stream number and adding to a set; if the 
			//  add fails, then it is a duplicate and should be skipped
			final String[] tokens = srtFile.getAbsolutePath().split( "\\." ) ;
			if( tokens.length < 3 )
			{
				log.warning( "Invalid number of tokens in file: " + srtFile.getAbsolutePath() ) ;
				continue ;
			}
			
			int streamNumber = -1 ;
			try
			{
				streamNumber = Integer.valueOf( tokens[ tokens.length - 2 ] ) ;
			}
			catch( NumberFormatException theException )
			{
				log.warning( "Invalid stream number: " + srtFile.getAbsolutePath() ) ;
				continue ;
			}
//			log.info( "Found stream number " + streamNumber + " in file " + srtFile.getAbsolutePath() ) ;
	
			String newFileNameWithPath = "" ;
			
			// First, check for forced_subtitle
			if( srtFile.getAbsolutePath().contains( "forced_subtitle" ) )
			{
				// Verify the filename is exactly what I expect:
				// Game Of Thrones - S03E06 - The Climb.forced_subtitle.14.srt or Thor The Dark World (2010).forced_subtitle.2.srt
				if( tokens.length < 4 )
				{
					log.warning( "Invalid number of tokens for forced_subtitle file: " + srtFile.getAbsolutePath() ) ;
					continue ;
				}
				// >= 4 tokens
				final String fileWithoutStreamNumberOrExtension = srtFile.getAbsolutePath().replace( ".forced_subtitle." + streamNumber + ".srt", "" ) ;
				newFileNameWithPath = fileWithoutStreamNumberOrExtension + ".en.forced.srt" ;
			}
			else
			{
				// No forced subtitle
				// Already checked number of tokens.
				final String fileWithoutStreamNumberOrExtension = srtFile.getAbsolutePath().replace( "." + streamNumber + ".srt", "" ) ;
				newFileNameWithPath = fileWithoutStreamNumberOrExtension + ".en." + streamNumber + ".srt" ;
			}
//			log.info( "newFileNameWithPath: " + newFileNameWithPath ) ;

			// Rename the current srtFile to "OLD - " + srtFile name
			String archiveFileName = "" ;
			try
			{
				Path newFilePath = Paths.get( newFileNameWithPath ) ;
				Path srtFileParentPath = srtFile.toPath().getParent() ;
				archiveFileName = srtFileParentPath.toString() + "\\OLD - " + srtFile.getName() ;
				log.info( "srtFile: " + srtFile.getAbsolutePath() + ", archiveFileName: " + archiveFileName + ", newFilePath: " + newFilePath.toString() ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Error renaming " + srtFile.getAbsolutePath() + " to " + archiveFileName + ":" + theException.toString() ) ;
				continue ;
			}
			
		} // for( srtFile )
		log.info( "Found " + properlyFormedNames + " properly formed names in " + directory ) ;
	}
}
