package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RenameMovieEditions
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_rename_movie_editions.txt" ;

	public RenameMovieEditions()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		(new RenameMovieEditions()).run() ;
	}

	public void run()
	{
		common.setTestMode( false ) ;

		final String topDirectoryString = "\\\\skywalker\\Media\\Movies" ;
		final File topDirectoryFile = new File( topDirectoryString ) ;
		if( !topDirectoryFile.isDirectory() )
		{
			log.warning( "topDirectoryString does not represent a directory: " + topDirectoryString ) ;
			return ;
		}
		// Post-condition: topDirectoryFile is a directory
//		processSquareBrackets( topDirectoryFile ) ;
//		processCurlyBracketDirectories( topDirectoryFile ) ;
		processCurlyBracketFiles( topDirectoryFile ) ;
	}

	/**
	 * This method will look through all files in any subdirectory that contains {}.
	 * For each, it will ensure all curly bracket files in that directory match the same naming convention
	 *  as the directory.
	 * Note that this assumes the only curly bracket files in a directory are the instantiation of the directory
	 *  name itself.
	 * Pre-condition: topDirectoryFile is non-null and is a directory.
	 * @param topDirectoryFile
	 */
	public void processCurlyBracketFiles( final File topDirectoryFile )
	{
		final File[] movieDirectories = topDirectoryFile.listFiles() ;
		for( File movieDirectory : movieDirectories )
		{
			if( !movieDirectory.isDirectory() )
			{
				// Not a directory.
				// Skip it.
				continue ;
			}
			// Post-condition: movieDirectory is a directory.

			final String origDirectoryAbsolutePathString = movieDirectory.getAbsolutePath() ;
//			Path origPath = movieDirectory.toPath() ;

			if( !origDirectoryAbsolutePathString.contains( "{" ) )
			{
				// No curly brackets, no problem.
				continue ;
			}
			// Post-condition: movieDirectory contains a curly bracket

			// Look for files in movieDirectory that have curly brackets.
			List< Path > curlyFiles = null ;
			try
			{
				curlyFiles = common.findFiles( origDirectoryAbsolutePathString, ".*\\{.*\\}.*" ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Error in findFiles() for movieDirectory " + movieDirectory + ": " + theException.toString() ) ;
				continue ;
			}
			// Post-condition: curlyFiles is non-null
			
			for( Path curlyFilePath : curlyFiles )
			{
				// If the filename already has "edition-" in it, then it should be ok.
				// Note that the directory may have "edition-" in it, but the filename may not.
				final String fileNameWithoutPath = curlyFilePath.getFileName().toString() ;

				if( fileNameWithoutPath.contains( "edition-" ) )
				{
					// Nothing to do.
//					log.info( "Skipping curlyFilePath: " + curlyFilePath.toString() ) ;
					continue ;
				}
				log.fine( "Need to fix curlyFilePath: " + curlyFilePath.toString() ) ;
				
				// Get the edition information from the directory path
				// Extract the data between the curly brackets
				final Pattern curlyPattern = Pattern.compile( "\\{(.*)\\}" ) ;
				final Matcher curlyMatcher = curlyPattern.matcher( origDirectoryAbsolutePathString ) ;
				if( !curlyMatcher.find() )
				{
					log.warning( "Unable to find curly data for movie: " + origDirectoryAbsolutePathString ) ;
					continue ;
				}
				
				// The path will have two set of curly data, but group( 1) will choose the first (the directory name)
				final String correctCurlyData = curlyMatcher.group( 1 ).trim() ;
//				log.info( "correctCurlyData: " + correctCurlyData ) ;

				// Build the name of the new file with correct nomenclature.
				final Pattern oldNomenclaturePattern = Pattern.compile( "- \\{.*\\}" ) ;
				final Matcher oldNomenclatureMatcher = oldNomenclaturePattern.matcher( curlyFilePath.toString() ) ;
				final String newFileNameWithPath = oldNomenclatureMatcher.replaceAll( "{" + correctCurlyData + "}" ).replace( " .mkv", ".mkv" ) ;
				
				final File newFile = new File( newFileNameWithPath ) ;
				final Path newPath = newFile.toPath() ;
				
				log.info( "Renaming " + curlyFilePath.toString() + " to " + newPath.toString() ) ;
				
				if( !common.getTestMode() )
				{
					// Rename the files
					try
					{
						Files.move( curlyFilePath,  newPath, StandardCopyOption.REPLACE_EXISTING ) ;
					}
					catch( Exception theException )
					{
						log.warning( "Error moving " + curlyFilePath.toString() + " to " + newPath.toString() + ": " + theException.toString() ) ;
						continue ;
					}
				} // if( !getTestMode() )
			} // for( Path curlyFilePath : curlyFiles )			
		} // for( movieDirectory )
	}


	/**
	 * This method processes all directories with curly brackets. Early on, I accidentally added an extra " - " in the name and
	 *  forgot the "edition-" in the naming. Fix that here.
	 * Pre-condition: topDirectoryFile is non-null and is a directory.
	 */
	public void processCurlyBracketDirectories( final File topDirectoryFile )
	{
		final File[] movieDirectories = topDirectoryFile.listFiles() ;
		for( File movieDirectory : movieDirectories )
		{
			if( !movieDirectory.isDirectory() )
			{
				// Not a directory.
				// Skip it.
				continue ;
			}
			// Post-condition: movieDirectory is a directory.

			final String origDirectoryAbsolutePathString = movieDirectory.getAbsolutePath() ;
			Path origPath = movieDirectory.toPath() ;

			if( !origDirectoryAbsolutePathString.contains( "{" ) )
			{
				// No curly brackets, no problem.
				continue ;
			}
			// Post-condition: movieDirectory contains a curly bracket
			
			if( origDirectoryAbsolutePathString.contains( "{edition-" ) )
			{
				// Directory name appears to be in the right format. Nothing to do.
				continue ;
			}
			// Post-condition: directory name has curly brackets and is missing "{edition-".
			// The directory name needs to be renamed.

			// Get the name through the closing parantheses
			final int closingParanIndex = origDirectoryAbsolutePathString.indexOf( ")" ) ;
			if( -1 == closingParanIndex )
			{
				log.warning( "Unable to find ')' in movieDirectory: " + origDirectoryAbsolutePathString ) ;
				continue ;
			}

			final String directoryWithPathNoEdition = origDirectoryAbsolutePathString.substring( 0, closingParanIndex + 1 ) ;
			//			log.info( "origDirectoryAbsolutePathString: " + origDirectoryAbsolutePathString ) ;

			// Extract the data between the curly brackets
			Pattern curlyPattern = Pattern.compile( "\\{(.*)\\}" ) ;
			Matcher curlyMatcher = curlyPattern.matcher( origDirectoryAbsolutePathString ) ;
			if( !curlyMatcher.find() )
			{
				log.warning( "Unable to find curly data for movie: " + origDirectoryAbsolutePathString ) ;
				continue ;
			}
			final String curlyData = curlyMatcher.group( 1 ) ;
			//			log.info( "curlyData: " + curlyData ) ;

			// Got the edition information, now assemble a new directory name.
			final String newDirectoryNameWithPath = directoryWithPathNoEdition + " {edition-" + curlyData + "}" ;
			final File newDirectoryFile = new File( newDirectoryNameWithPath ) ;
			final Path newDirectoryPath = newDirectoryFile.toPath() ;
			log.info( "Moving directory " + origPath.toString() + " to " + newDirectoryPath.toString() ) ;

			try
			{
				if( !common.getTestMode() )
				{
					Files.move( origPath, newDirectoryPath, StandardCopyOption.REPLACE_EXISTING ) ;
				}
			}
			catch( Exception theException )
			{
				log.warning( "Unable to move file " + origPath.toString() + " to " + newDirectoryNameWithPath.toLowerCase() + ": " + theException.toString() ) ;
				continue ;
			}

		} // for( movieDirectory )

	}

	/**
	 * This method processes all files with square brackets in them. This is generally to rename things to use curly brackets and add the "edition" tag.
	 * @param topDirectoryFile
	 * Pre-condition: topDirectoryFile is non-null and is a directory.
	 */
	public void processSquareBrackets( final File topDirectoryFile )
	{
		final String topDirectoryString = topDirectoryFile.getAbsolutePath() ;

		// Walk through the top directory looking for subdirectories.
		// Each subdirectory, because this is intended for movies only, should be a movie directory.
		final File[] movieDirectories = topDirectoryFile.listFiles() ;
		for( File movieDirectory : movieDirectories )
		{
			if( !movieDirectory.isDirectory() )
			{
				// Not a directory.
				// Skip it.
				continue ;
			}
			// Post-condition: movieDirectory is a directory.

			// Check if the movieDirectory contains a file with square brackets.
			// Square brackets should mean the directory has at least one file with a non-theatrical release edition
			//  that has not yet been renamed.
			File bracketFile = findBracketFile( movieDirectory ) ;
			if( null == bracketFile )
			{
				// No bracket files in this directory.
				// Skip it.
				continue ;
			}
			// Post-condition: Found at least one bracket file in this directory.

			// Need to determine the edition, get all matching files for this bracket (srt files), create a new directory with the edition information,
			//  rename/move the files with the edition information.

			// Remove the extension.
			final String fileName = bracketFile.getName() ;
			// Strip the extension and '.'
			final String fileNameWithoutExtension = fileName.substring( 0, fileName.length() - 4 ) ;
			List< Path > bracketFiles = null ;
			try
			{
				// The name of the movie likely has parantheses and potentially other characters
				// that regex will interpret as special characters. Use the Pattern.quote() method
				// to replace each regex special character with a literal.
				final String pattern = Pattern.quote( fileNameWithoutExtension ) + "\\..*" ;
				bracketFiles = common.findFiles( movieDirectory.getAbsolutePath(), pattern ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Exception searching for bracketFiles: " + theException.toString() ) ;
				continue ;
			}
			// Post-condition: bracketFiles != null and contains a list of files associated with this edition.
			// Note that all of the returned files have the same edition information.

			// Next, identify the special edition that is contained within the square brackets.
			// The square brackets could be anywhere in the file name (before or after the year).
			final String bracketRegex = "\\[.*\\]" ;
			Pattern bracketRegexPattern = Pattern.compile( bracketRegex ) ;
			Matcher matcher = bracketRegexPattern.matcher( fileName ) ;

			// Because we already searched for brackets, matcher should be non-null
			assert( matcher != null ) ;
			String editionWithBrackets = "NOT FOUND" ;
			if( matcher.find() )
			{
				editionWithBrackets = matcher.group( 0 ) ;
			}
			final String editionWithoutBrackets = editionWithBrackets.substring( 1, editionWithBrackets.length() - 1 ) ;
			//			log.info( "editionWithBrackets: " + editionWithBrackets + ", editionWithoutBrackets: " + editionWithoutBrackets ) ;

			// Build the new path
			// First, build the directory name by starting with the filename and removing the edition.
			String newDirectoryName = fileNameWithoutExtension.replace( editionWithBrackets, "" ) + " {edition-" + editionWithoutBrackets + "}" ; 

			// Remove double spaces
			newDirectoryName = newDirectoryName.replaceAll( "\\s{2,}", " " ) ;

			final String newDirectoryAbsolutePath = common.addPathSeparatorIfNecessary( topDirectoryString ) + newDirectoryName ;
			File newDirectoryFile = new File( newDirectoryAbsolutePath ) ;
			log.info( "newDirectoryFile: " + newDirectoryFile.getAbsolutePath() ) ;

			// Rename and move each file in the movieDirectory that matches the edition (bracketFiles)
			for( Path bracketPath : bracketFiles )
			{
				File origFile = bracketPath.toFile() ;
				String origFileName = origFile.getName() ;

				// Convert to new naming.
				// Remove the old edition and any double spaces.
				final String origFileNameWithoutBrackets = origFileName.replace( editionWithBrackets, "" ).replaceAll( "\\s{2,}", " " ) ;

				// Add the new edition information after the closing paranthesis of the year.
				final int closingParanIndex = origFileNameWithoutBrackets.indexOf( ")" ) ;
				StringBuilder nameBuilder = new StringBuilder( origFileNameWithoutBrackets ) ;
				nameBuilder.insert( closingParanIndex + 1,  " {edition-" + editionWithoutBrackets + "}" ) ;
				final String newFileName = nameBuilder.toString() ;
				log.fine( "newFileName: " + newFileName ) ;

				// Now create the full path.
				final String newFileNamewithPath = common.addPathSeparatorIfNecessary( newDirectoryAbsolutePath ) + newFileName ;
				File newFile = new File( newFileNamewithPath ) ;
				log.info( "newFile: " + newFile.getAbsolutePath() ) ;

				Path origFilePath = bracketPath ;
				Path newFilePath = newFile.toPath() ;
				log.info( "Moving " + origFilePath.toString() + " to " + newFilePath.toString() ) ;

				if( !common.getTestMode() )
				{
					try
					{
						if( !newDirectoryFile.exists() )
						{
							newDirectoryFile.mkdir() ;
						}
						Files.move( origFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING ) ;
					}
					catch( Exception theException )
					{
						log.warning( "Exception making directory or moving file: " + theException.toString() ) ;
					}
				} // if( !common.getTestMode() )
			} // for( bracketFiles )
		} // for( movieDirectories )
	}

	/**
	 * Search in the movieDirectory for any mkv or mp4 file that contains a square bracket in its name. Such a file represents a non-theatrical release edition
	 *  that needs to be modified. Note that each directory may have multiple such bracket files -- just run the method more than once.
	 * @param movieDirectory
	 * @return
	 */
	public File findBracketFile( final File movieDirectory )
	{
		assert( movieDirectory != null ) ;

		File retMe = null ;
		try
		{
			List< Path > pathList = common.findFiles( movieDirectory.getAbsolutePath(), "^.*\\[.*\\].*(mkv|mp4)$" ) ;
			for( Path thePath : pathList )
			{
				log.info( "Found bracket file: " + thePath.toString() ) ;
			}

			// For the purposes of this method, just get the first such item, if any exist.
			if( !pathList.isEmpty() )
			{
				final Path firstPath = pathList.getFirst() ;
				retMe = firstPath.toFile() ;
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception while searching for bracketFile: " + theException.toString() ) ;
			return null ;
		}
		return retMe ;
	}

	/**
	 * 
	 * @param movieDirectory
	 */
	public void processMovieDirectory( final File movieDirectory )
	{

	}

}
