package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.WordUtils;

import com.google.gson.Gson;

public class FixTVShowNaming
{
	public TheTVDB tvDB = null ;

	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_fix_tv_show_naming.txt" ;

	protected final String[] allowableExtensions = {
			"mp4",
			"mkv",
			"srt"
	} ;

	protected final String[] tvDirectories = {
			//			"\\\\skywalker\\usbshare1-2\\TV",
			//			"\\\\skywalker\\Media\\Staging\\Test"
			//			"\\\\skywalker\\Media\\Staging\\TV_Shows",
			"\\\\skywalker\\Media\\TV_Shows\\House Of The Dragon (2022)\\Season 01"
	} ;
	protected final String[] extensions = {
			"mkv",
			"mp4"
	} ;

	// Pattern matching
	public class TV_Show_Pattern
	{
		/// Top level match to determine if the file is named incorrectly
		public String badPatternMatch = "" ;

		/// Patterns to extract show name, season and episode number, and episode name
		public String inputShowName = "" ;
		public String inputSeasonAndEpisodeNumber = "" ;
		public String inputEpisodeName = "" ;
		public String inputExtension = "" ;

		/// Output show name, season and episode number, and episode name
		public String outputShowName = "" ;
		public String outputSeasonAndEpisodeNumber = "" ;
		public String outputEpisodeName = "" ;
		public String outputExtension = "" ;

		public TV_Show_Pattern( final String badPatternMatch,
				final String inputShowName,
				final String inputSeasonAndEpisodeNumber,
				final String inputEpisodeName,
				final String inputExtension )
		{
			this.badPatternMatch = badPatternMatch ;
			this.inputShowName = inputShowName ;
			this.inputSeasonAndEpisodeNumber = inputSeasonAndEpisodeNumber ;
			this.inputEpisodeName = inputEpisodeName ;
			this.inputExtension = inputExtension ;
		}
		
		public String toString()
		{
			Gson loginRequestGson = new Gson() ;
			final String loginRequestJson = loginRequestGson.toJson( this ) ;
			return loginRequestJson.toString() ;
		}
	}


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

		TV_Show_Pattern[] tvShowPatterns =
			{
					// House of the Dragon_S01E01_The Heirs of the Dragon.mp4
					new TV_Show_Pattern( ".*_S[\\d]+E[\\d]+_.*\\..*", "(.*)_S[\\d]+E[\\d]+_.*\\..*", ".*_(S[\\d]+E[\\d]+)_.*", ".*_S[\\d]+E[\\d]+_(.*)\\..*", ".*_S[\\d]+E[\\d]+_.*\\.(.*)" )
			} ;

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

			// List all matching files.
//			List< Path > pathList = common.findFiles( tvDirectoryFile.getAbsolutePath(), "^.*(mkv|mp4)$" ) ;
			List< Path > pathList = common.findFiles( tvDirectoryFile.getAbsolutePath(), "^.*\\_.*(mkv|mp4|srt)$" ) ;
			for( Path inputFilePath : pathList )
			{
				log.fine( "inputFilePath: " + inputFilePath.toString() ) ;
				final String inputFileName = inputFilePath.getFileName().toString() ;

				for( TV_Show_Pattern tvShowPattern : tvShowPatterns )
				{
					Pattern inputFileNamePattern = Pattern.compile( tvShowPattern.badPatternMatch ) ;
					Matcher inputFileNameMatcher = inputFileNamePattern.matcher( inputFileName ) ;
					if( !inputFileNameMatcher.find() )
					{
						// No match
						log.info( "No Match" ) ;
						continue ;
					}
					log.fine( "Found match between inputFileName " + inputFileName + " and badPatternMatch " + tvShowPattern.badPatternMatch ) ;
					
					// Extract the elements of the file name
					Pattern showNamePattern = Pattern.compile( tvShowPattern.inputShowName ) ;
					Matcher showNameMatcher = showNamePattern.matcher( inputFileName ) ;
					if( !showNameMatcher.find() )
					{
						log.warning( "Unable to find show name in " + inputFileName ) ;
						continue ;
					}
					String showName = showNameMatcher.group( 1 ) ;
					showName = WordUtils.capitalize( showName ) ;
					
					Pattern seasonAndEpisodeNumberPattern = Pattern.compile( tvShowPattern.inputSeasonAndEpisodeNumber ) ;
					Matcher seasonAndEpisodeNumberMatcher = seasonAndEpisodeNumberPattern.matcher( inputFileName ) ;
					if( !seasonAndEpisodeNumberMatcher.find() )
					{
						log.warning( "Unable to find seasonAndEpisodeNumber in " + inputFileName ) ;
						continue ;
					}
					String seasonAndEpisodeNumber = seasonAndEpisodeNumberMatcher.group( 1 ) ;
					seasonAndEpisodeNumber =  WordUtils.capitalize( seasonAndEpisodeNumber ) ;
					
					Pattern episodeNamePattern = Pattern.compile( tvShowPattern.inputEpisodeName ) ;
					Matcher episodeNameMatcher = episodeNamePattern.matcher( inputFileName ) ;
					if( !episodeNameMatcher.find() )
					{
						log.warning( "Unable to find episodeName in " + inputFileName ) ;
						continue ;
					}
					String episodeName = episodeNameMatcher.group( 1 ) ;
					episodeName =  WordUtils.capitalize( episodeName ) ;
					
					Pattern extensionPattern = Pattern.compile( tvShowPattern.inputExtension ) ;
					Matcher extensionMatcher = extensionPattern.matcher( inputFileName ) ;
					if( !extensionMatcher.find() )
					{
						log.warning( "Unable to find extension in " + inputFileName ) ;
						continue ;
					}
					String extension = extensionMatcher.group( 1 ) ;
					extension =  WordUtils.capitalize( extension ) ;
					
					log.fine( "showName: " + showName + ", seasonAndEpisodeNumber: " + seasonAndEpisodeNumber + ", episodeName: " + episodeName ) ;
					final String newFileName = showName + " - " + seasonAndEpisodeNumber + " - " + episodeName + "." + extension ;
					final String newFileWithPath = common.addPathSeparatorIfNecessary( inputFilePath.getParent().toString() ) + newFileName ;
					final File newFile = new File( newFileWithPath ) ;
					
					log.info( "Moving " + inputFilePath.toString() + " to " + newFileWithPath ) ;
					if( !common.getTestMode() )
					{
						try
						{
							Files.move( inputFilePath, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
						}
						catch( Exception theException )
						{
							log.warning( "Unable to move file from " + inputFilePath.toString() + " to " + newFile.getAbsolutePath() + ": " + theException.toString() ) ;
							continue ;
						}
					} // getTestMode()
				} // for( tvShowPattern )
			} // for( inputFilePath )

			// For each file, check if it matches a bad pattern.

			// Find all directories inside of this directory
			//			File[] firstLevelFiles = tvDirectoryFile.listFiles() ;
			//			for( File theFile : firstLevelFiles )
			//			{
			//				if( !theFile.isDirectory() )
			//				{
			//					continue ;
			//				}
			//				// current file is a directory and should be a tv show name
			//				TheTVDB_ShowInfo showInfo = tvDB.getFullShowInfo( theFile.getName() ) ;
			////				log.info( "Found show: " + theFile.getName() ) ;
			//
			//				processTVShow( theFile, showInfo ) ;
			//			}
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
		final String seasonNumberString = oldSeasonAndEpisodeNumbers.substring( 1, 3 ) ;
		final String episodeNumberString = oldSeasonAndEpisodeNumbers.substring( 4, 6 ) ;

		final Integer seasonNumberInteger = Integer.valueOf( seasonNumberString ) ;
		final Integer episodeNumberInteger = Integer.valueOf( episodeNumberString ) ;

		// Check for the instance where the file name is like this:
		// The West Wing - S01E01 - The West Wing (S1E1) Pilot.mp4
		final String duplicateCheckString = tvShowName + " (S" + seasonNumberInteger.toString() + "E" + episodeNumberInteger.toString() + ") " ;
		if( episodeName.contains( duplicateCheckString ) )
		{
			episodeName = episodeName.replace( duplicateCheckString, "" ) ;
			log.info( "Found internal duplicate name: " + oldFileName ) ;
		}

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
