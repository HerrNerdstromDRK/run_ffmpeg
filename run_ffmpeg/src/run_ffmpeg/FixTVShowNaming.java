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

	protected final String[] allowableExtensions = {
			"mp4",
			"mkv",
			"srt"
	} ;

	protected final String[] tvDirectories = {
			//			"\\\\skywalker\\usbshare1-2\\TV",
						"\\\\skywalker\\Media\\TV_Shows",
//			"\\\\skywalker\\Media\\TV_Shows\\Star Wars Andor (2022)"
	} ;
	protected final String[] extensions = {
			"mkv",
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
		common.setTestMode( false ) ;

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
//				log.info( "Found show: " + theFile.getName() ) ;

				processTVShow( theFile, showInfo ) ;
			}
		} // for( tvDirectory )
	}

	/**
	 * Return true if the given inputExtension is an allowable extension.
	 * Match is performed case-insensitive.
	 * @param inputExtension
	 * @return
	 */
	public boolean isAllowableExtension( final String inputExtension )
	{
		assert( inputExtension != null ) ;
		for( String allowableExtension : allowableExtensions )
		{
			if( allowableExtension.equalsIgnoreCase( inputExtension ) )
			{
				// inputExtension is an allowable extension.
				return true ;
			}
		}
		return false ;
	}

	public String fixTVShowFileName( final String oldFileName, TheTVDB_ShowInfo showInfo )
	{
		assert( oldFileName != null ) ;
		assert( showInfo != null ) ;

		//		log.info( "Fixing file name: " + oldFileName ) ;
		String newFileName = stripInvalidSubStrings( oldFileName ) ;

		// Sometimes the show or episode name will have one or more periods (.) in the name.
		// All we really care about here is the extension.
		final String[] fileAndExtensionTokens = newFileName.split( "\\." ) ;
		// fileAndExtensionTokens: ["Show Name - S01E02 - Pilot", "mp4"]
		if( fileAndExtensionTokens.length < 2 )
		{
			log.warning( "Unable to find file name and extension tokens for file " + newFileName ) ;
			return oldFileName ;
		}
		final String extension = fileAndExtensionTokens[ fileAndExtensionTokens.length - 1 ] ;

		final String[] dashTokens = newFileName.substring( 0, newFileName.length() - 4 ).split( " - " ) ;
		// dashTokens: ["Show Name", "S01E02", "Pilot"]
		//		log.info( "oldFileName: " + oldFileName + ", dashTokens: " + Arrays.toString( tokens ) ) ;

		if( dashTokens.length != 3 )
		{
			log.warning( "Invalid number of tokens (" + dashTokens.length + ") for file name: " + oldFileName ) ;
			return oldFileName ;
		}
		String tvShowName = dashTokens[ 0 ] ; // "Show Name"
		if( tvShowName.contains( "(" ) && tvShowName.contains( ")" ) )
		{
			// While the tv show directory name may have a date ("(2021)"), I prefer
			// the show name for the actual file exclude the date.
			// I don't know why, I just do.
			tvShowName = tvShowName.substring( 0, tvShowName.length() - 7 ) ;
		}
		
		final String oldSeasonAndEpisodeNumbers = dashTokens[ 1 ] ; // "S01E02"
		String episodeName = dashTokens[ 2 ] ; // 

		// Parse the season and episode number
//		final String seasonNumberString = oldSeasonAndEpisodeNumbers.substring( 1, 3 ) ;
//		final String episodeNumberString = oldSeasonAndEpisodeNumbers.substring( 4, 6 ) ;

		//		final Integer seasonNumberInteger = Integer.valueOf( seasonNumberString ) ;
		//		final Integer episodeNumberInteger = Integer.valueOf( episodeNumberString ) ;

		//		final TheTVDB_SeasonInfo seasonInfo = showInfo.getSeasonInfo( seasonNumberInteger ) ;
		//		assert( seasonInfo != null ) ;

		//		String episodeName = seasonInfo.getEpisodeName( episodeNumberInteger ) ;
		tvShowName = WordUtils.capitalize( tvShowName ) ;		
		
		if( episodeName != null )
		{
			// It's possible for an episode name to be null because of how the old file names are written (they are extras)
			// Strip invalid characters and convert to title case
			episodeName = stripInvalidSubStrings( episodeName ) ;
			episodeName = WordUtils.capitalize( episodeName ) ;
		}

		// Build the new full name string
		final String newSeasonAndEpisodeNumbers = oldSeasonAndEpisodeNumbers ;
		final String newTVEpisodeName = episodeName ;

		newFileName = tvShowName + " - " + newSeasonAndEpisodeNumbers + " - " + newTVEpisodeName + "." + extension ;
		log.fine( "Transform: " + oldFileName + ": " + newFileName ) ;

		if( newFileName.equals( oldFileName ) )
		{
			// No changes, no need to change the file name
			newFileName = "" ;
		}
		return newFileName ;
	}

	public String fixMovieFileName( final String oldFileName )
	{
		return oldFileName ;
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
					if( !common.getTestMode() )
					{
						theFile.renameTo( newFile ) ;
					}
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
		//			retMe = retMe.replace( "- ", " " ) ;
		retMe = retMe.replace( "  ", " " ) ;
		retMe = retMe.replace( "\'", "" ) ;
		retMe = retMe.replace( "!", "" ) ;
		retMe = retMe.replace( ",", "" ) ;
		retMe = retMe.replace( "\'", "" ) ;
		retMe = retMe.replace( "?", "" ) ;
		retMe = retMe.replace( "#", "" ) ;
		retMe = retMe.replace( ":", "" ) ;
		retMe = retMe.replace( "...", "" ) ;
		retMe = retMe.replace( "*", "" ) ;

		return retMe ;
	}
}
