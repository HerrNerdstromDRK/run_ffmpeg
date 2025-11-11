package run_ffmpeg;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

public class WorkflowStageThread_MadeMovieChoice extends WorkflowStageThread
{
	/// The createSRTWithOCRCollection is the collection of file names with paths to OCR.
	protected transient MongoCollection< JobRecord_MadeMovieChoice > madeMovieChoiceCollection = null ;

	public WorkflowStageThread_MadeMovieChoice( final String threadName, Logger log, Common common, MoviesAndShowsMongoDB masMDB )
	{
		super( threadName, log, common, masMDB ) ;
		initObject() ;
	}

	private void initObject()
	{
		madeMovieChoiceCollection = masMDB.getAction_MadeMovieChoiceCollection() ;
	}

	/**
	 * Override this method. This method performs the work intended for subclass instances of this class.
	 * @return true if work completed false if no work completed.
	 */
	public boolean doAction()
	{
		final JobRecord_MadeMovieChoice madeMovieChoiceRecord = madeMovieChoiceCollection.findOneAndDelete( null ) ;
		if( null == madeMovieChoiceRecord )
		{
			// No more entries left to process.
			return false ;
		}
		setWorkInProgress( true ) ;

		final String inputDirectoryAbsolutePath = madeMovieChoiceRecord.getInputDirectoryAbsolutePath() ;
		final File inputDirectoryFile = new File( inputDirectoryAbsolutePath ) ;
		if( !inputDirectoryFile.exists() || !inputDirectoryFile.isDirectory() )
		{
			log.warning( "Error with inputDirectoryFile: " + inputDirectoryFile.getAbsolutePath() ) ;
			setWorkInProgress( false ) ;
			return false ;
		}
		
//		final String oldDirectoryName = inputDirectoryFile.getName() ;
		DirectoryNamePattern dnp = new DirectoryNamePattern( log, inputDirectoryFile ) ;
		
		// Possible that the directory has already been corrected by another process, so double
		// check if it actually needs to be fixed.
		if( dnp.hasTmdbInfo() )
		{
			// Already in good order
			setWorkInProgress( false ) ;
			return true ;
		}
		
		final Long tmdbID = madeMovieChoiceRecord.getTmdbID() ;
		dnp.setTmdbInfo( Long.toString( tmdbID ) ) ;
		dnp.setHasTmdbInfo( true ) ;
		final String newDirectoryName = dnp.getDirectoryName() ;
		log.info( "newDirectoryName: " + newDirectoryName ) ;
		
		// Replace all files in the directory that match the show or movie name with the updated name, then
		// change the name of the directory itself.
		// 		final Path oldTargetDirectoryPath = Paths.get( common.addPathSeparatorIfNecessary( inputDirectoryFile.getParentFile() ) 
		final Path oldTargetDirectoryPath = Paths.get( inputDirectoryFile.getAbsolutePath() ) ;

		// Find the matching files. It needs to be against the most specific name possible so I avoid catching similar files.
		// For example, Zoolander.* matches to Zoolander-behindthescenes. Instead, search for "Zoolander (2001) {imdb-...}"
		final List< File > matchingFiles = common.findFilesThatStartWith( inputDirectoryAbsolutePath, inputDirectoryFile.getName() ) ; 
		for( File matchingFile : matchingFiles )
		{
			final String oldFileName = matchingFile.getName() ;
			
			// Replace the old name with the new name
			// The intent here is to keep all of the suffixes (.en.srt, .en.8.srt, .mkv, .mp4, etc.) and replace everything in the
			//  filename leading up to the extension.
			final int firstDotIndex = oldFileName.indexOf( '.' ) ;
			final String extensions = oldFileName.substring( firstDotIndex ) ;
			final String newFileName = newDirectoryName + extensions ;
			Path newFilePath = oldTargetDirectoryPath.resolve( newFileName ) ;
			
			log.info( "Renaming file from " + matchingFile.getAbsolutePath() + " to " + newFilePath.toString() ) ;
			if( !common.getTestMode() )
			{
				try
				{
					Files.move( matchingFile.toPath(), newFilePath ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Error renaming file: " + theException.toString() ) ;
				}
			}			
		} // for( matchingFilePath )
		
		// Now rename the folder
		final Path parentDirectoryPath = oldTargetDirectoryPath.getParent() ;
		final String parentDirectoryPathString = parentDirectoryPath.toString() ;
		final String newDirectoryPathString = common.addPathSeparatorIfNecessary( parentDirectoryPathString ) + newDirectoryName ;
		final File newDirectoryFile = new File( newDirectoryPathString ) ;
		final Path newDirectoryPath = newDirectoryFile.toPath() ;
		
		log.info( "Renaming directory from " + oldTargetDirectoryPath.toString() + " to " + newDirectoryPath.toString() ) ;
		if( !common.getTestMode() )
		{
			try
			{
				Files.move( oldTargetDirectoryPath, newDirectoryPath ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Failed to rename directory: " + theException.toString() ) ;
			}
		}
		
		setWorkInProgress( false ) ;
		return true ;
	}

	@Override
	public String getUpdateString()
	{
		return "Database size: " + madeMovieChoiceCollection.countDocuments() ;
	}
}
