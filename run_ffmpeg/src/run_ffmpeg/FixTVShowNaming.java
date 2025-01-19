package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.text.WordUtils;

public class FixTVShowNaming
{
	public TheTVDB tvDB = null ;

	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_rename_from_josh_to_dan.txt" ;

	protected final String[] tvDirectories = {
			"\\\\skywalker\\usbshare1-2\\TV"
	} ;
	protected final String[] extensions = {
			"mp4"
	} ;

	public static void main( String[] args )
	{
		(new FixTVShowNaming()).run() ;
	}

	public FixTVShowNaming()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		tvDB = new TheTVDB() ;
	}

	public void run()
	{
		for( String tvDirectory : tvDirectories )
		{
			File tvDirectoryFile = new File( tvDirectory ) ;
			if( !tvDirectoryFile.exists() )
			{
				log.warning( "Unable to build tvDirectoryFile for tvDirectory: " + tvDirectory ) ;
				continue ;
			}

			if( !tvDirectoryFile.isDirectory() )
			{
				continue ;
			}
			// tvDirectoryFile is a valid directory

			// Find all directories inside of this directory
			File[] firstLevelFiles = tvDirectoryFile.listFiles() ;
			for( File theFile : firstLevelFiles )
			{
				if( !theFile.isDirectory() )
				{
					continue ;
				}
				// current file is a directory and should be a tv show name
				TheTVDB_ShowInfo showInfo = tvDB.getFullShowInfo( theFile.getName() ) ;
				log.info( "Found show: " + theFile.getName() ) ;

				processTVShow( theFile, showInfo ) ;
			}
		} // for( tvDirectory )
	}

	public void processTVShow( final File tvDirectoryFile, TheTVDB_ShowInfo showInfo )
	{
		assert( tvDirectoryFile != null ) ;
		assert( showInfo != null ) ;

		log.info( "Scanning TV directory: " + tvDirectoryFile ) ;
		final List< File > matchingFiles = new ArrayList< File >() ;

		matchingFiles.addAll( common.getFilesInDirectoryByExtension( tvDirectoryFile.getAbsolutePath(), extensions ) ) ;
		log.info( "Found " + matchingFiles.size() + " matching file(s)" ) ;

		//		log.info( "Found matching files: " ) ;
		for( File theFile : matchingFiles )
		{
			//			log.info( theFile.getAbsolutePath() ) ;
			final String oldFileName = theFile.getName() ;
			final String newFileName = fixTVShowFileName( oldFileName, showInfo ) ;
			
			if( (newFileName != null) && !newFileName.isEmpty() )
			{
				// Rename the file.
				final String newFileNameWithPath = theFile.getParent() + "\\" + newFileName ;
				
				try
				{
					File newFile = new File( newFileNameWithPath ) ;
					log.info( "Renaming file " + theFile.toString() + " to: " + newFile.toString() ) ;
//					theFile.renameTo( newFile ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Unable to rename old file " + theFile.toString() + " to new file " + newFileNameWithPath + " because: " + theException.toString() ) ;
					break ;
				}
			}
		}
	}

	public String stripInvalidSubStrings( final String inputFileName )
	{
		assert( inputFileName != null ) ;

		String retMe = inputFileName ;
		retMe = retMe.replace( ",", "" ) ;

		// Remove '+' (M+A+S+H)
		retMe = retMe.replace( "+", "" ) ;
		retMe = retMe.replace( "&", "" ) ;
		retMe = retMe.replace( "--", "" ) ;
		retMe = retMe.replace( "- ", " " ) ;
		retMe = retMe.replace( "  ", " " ) ;
		retMe = retMe.replace( "\'", "" ) ;
		retMe = retMe.replace( "!", "" ) ;
		retMe = retMe.replace( "P.I.", "PI." ) ;
		retMe = retMe.replace( ",", "" ) ;
		retMe = retMe.replace( "\'", "" ) ;
		retMe = retMe.replace( "?", "" ) ;
		retMe = retMe.replace( "#", "" ) ;
		retMe = retMe.replace( ":", "" ) ;
		retMe = retMe.replace( "...", "" ) ;
		retMe = retMe.replace( "*", "" ) ;
//		retMe = retMe.replace( ".", "" ) ;

		return retMe ;
	}

	public String fixTVShowFileName( final String oldFileName, TheTVDB_ShowInfo showInfo )
	{
		assert( oldFileName != null ) ;
		assert( showInfo != null ) ;

		//		log.info( "Fixing file name: " + oldFileName ) ;
		String newFileName = stripInvalidSubStrings( oldFileName ) ;

		final String[] tokens = newFileName.split( "\\." ) ;
		//		log.info( "oldFileName: " + oldFileName + ", tokens: " + Arrays.toString( tokens ) ) ;

		if( tokens.length != 3 )
		{
			log.warning( "Invalid number of tokens (" + tokens.length + ") for file name: " + oldFileName ) ;
			return oldFileName ;
		}
		final String tvShowName = tokens[ 0 ] ;
		// season and episode names should be of the form "S0XE0Y"		
		final String oldSeasonAndEpisodeName = tokens[ 1 ] ;
		final String extension = tokens[ 2 ] ;

		// Parse the season and episode number
		final String seasonNumberString = oldSeasonAndEpisodeName.substring( 1, 3 ) ;
		final String episodeNumberString = oldSeasonAndEpisodeName.substring( 4, 6 ) ;

		final Integer seasonNumberInteger = Integer.valueOf( seasonNumberString ) ;
		final Integer episodeNumberInteger = Integer.valueOf( episodeNumberString ) ;

		final TheTVDB_SeasonInfo seasonInfo = showInfo.getSeasonInfo( seasonNumberInteger ) ;
		assert( seasonInfo != null ) ;

		String episodeName = seasonInfo.getEpisodeName( episodeNumberInteger ) ;

		if( episodeName != null )
		{
			// It's possible for an episode name to be null because of how the old file names are written (they are extras)
			// Strip invalid characters and convert to title case
			episodeName = stripInvalidSubStrings( episodeName ) ;
			episodeName = WordUtils.capitalize( episodeName ) ;
		}

		// Build the new full name string
		String newSeasonAndEpisodeName = oldSeasonAndEpisodeName ;
		String newTVEpisodeName = episodeName ;

		newFileName = tvShowName + " - " + newSeasonAndEpisodeName + " - " + newTVEpisodeName + "." + extension ;
		log.fine( "Transform: " + oldFileName + ": " + newFileName ) ;
		return newFileName ;
	}

	public String fixMovieFileName( final String oldFileName )
	{
		return oldFileName ;
	}
}
