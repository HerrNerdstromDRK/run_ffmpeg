package run_ffmpeg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A worker thread to move files.
 * @author Dan
 *
 */
public class MoveFilesWorkerThread extends run_ffmpegWorkerThread
{
	// Reference to the controller thread for shouldKeepRunning()
	private transient MoveFiles theController = null ;

	/// Work queue
	protected List< MoveFileInfo > moveActionList = new ArrayList< MoveFileInfo >() ;

	/// Variable the controller thread can use to shutdown the thread.
	protected boolean doKeepRunning = true ;

	public MoveFilesWorkerThread( MoveFiles theController, Logger log, Common common )
	{
		super( log, common ) ;

		assert( theController != null ) ;
		this.theController = theController ;
	}

	public void addFileToMove( final String inputFileNameWithPath, final String destinationFileNameWithPath )
	{
		MoveFileInfo moveFileInfo = new MoveFileInfo( inputFileNameWithPath, destinationFileNameWithPath ) ;
		synchronized( moveActionList )
		{
			moveActionList.add( moveFileInfo ) ;
		}
	}

	@Override
	public void run()
	{
		while( shouldKeepRunning() )
		{
			MoveFileInfo moveInfo = getNextWorkItem() ;
			try
			{
				if( null == moveInfo )
				{
					Thread.sleep( 100 ) ;
					continue ;
				}
			}
			catch( Exception theException )
			{
				log.warning( getName() + " Exception in first sleep(): " + theException.toString() ) ;
				continue ;
			}

			if( moveInfo.getSourceFilePath().equals( moveInfo.getDestinationFilePath() ) )
			{
				// Source and destination files are the same.
				continue ;
			}

			// Verify the source file exists
			if( !moveInfo.getSourceFile().exists() )
			{
				log.warning( "Source file does not exist: " + moveInfo.getSourceFile().getAbsolutePath() ) ;
			}

			// Verify the destination directory exists
			if( !Files.exists( moveInfo.getDestinationFileDirectoryPath() ) )
			{
				// Destination directory does *not* exist
				log.info( getName() + " Creating destinationFileDirectoryPath: " + moveInfo.getDestinationFileDirectoryPath()
				+ " " + toString() ) ;
				if( !common.getTestMode())
				{
					try
					{
					// Create the destination directory
					Files.createDirectory( moveInfo.getDestinationFileDirectoryPath() ) ;
					}
					catch( Exception theException )
					{
						log.warning( getName() + " Failed to create director " + moveInfo.getDestinationFileDirectoryPath() + ":" + theException.toString() ) ;
					}
				}
			}

			// Commence the move.
			log.info( getName() + " Moving " + moveInfo.getSourceFilePath().toString()
					+ " -> " + moveInfo.getDestinationFilePath().toString() ) ;

			Path temp = null ;
			final long startTime = System.nanoTime() ;
			try
			{
				if( common.isDoMoveFiles() )
				{
					temp = Files.move(	moveInfo.getSourceFilePath(), moveInfo.getDestinationFilePath() ) ;
				}
			}
			catch( Exception theException )
			{
				// Sometimes the transcode thread takes a short time to close the output (mp4) file.
				if( theException.toString().contains( "being used by another process" ) )
				{
					log.fine( getName() + " Sleeping until the owning process closes the file " + moveInfo.getSourceFilePath() ) ;

					// Need to wait for the owning process (probably transcode) to close the file.
					try
					{
						// Sleep to give the system some time to close the file.
						Thread.sleep( 100 ) ;

						// Add this action back to the list.
						synchronized( moveActionList )
						{
							moveActionList.add( moveInfo ) ;
						}
					}
					catch( Exception theNestedException )
					{
						log.warning( getName() + " Exceptoin in nested sleep: " + theNestedException.toString() ) ;
					}
				}
				// else -- do nothing.
			}

			final long endTime = System.nanoTime() ;
			final double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;
			final long fileLength = moveInfo.getDestinationFile().length() ;
			final double fileLengthInMB = fileLength / 1e6 ;
			final double MBPerSecond = fileLengthInMB / timeElapsedInSeconds ;
			log.info( getName() + " Successfully moved "
					+ moveInfo.getSourceFileNameWithPath() 
					+ " -> "
					+ moveInfo.getDestinationFileNameWithPath()
					+ "; elapsed time: "
					+ common.getNumberFormat().format( timeElapsedInSeconds )
					+ " seconds, "
					+ common.getNumberFormat().format( timeElapsedInSeconds / 60.0 )
					+ " minutes; moved " + fileLengthInMB + "MB at "
					+ common.getNumberFormat().format( MBPerSecond ) + "MB/sec"
					+ " " + toString() ) ;
			if( common.isDoMoveFiles() && (null == temp) )
			{
				log.warning( getName() + " Failed move: " + moveInfo.getSourceFilePath().toString()
						+ " -> " + moveInfo.getDestinationFilePath().toString() ) ;
			}
		} // while( keepRunning )
	} // run()

	/**
	 * Remove a move job from the action queue and return it.
	 * @return
	 */
	protected MoveFileInfo getNextWorkItem()
	{
		MoveFileInfo retMe = null ;
		synchronized( moveActionList )
		{
			if( !moveActionList.isEmpty() )
			{
				retMe = moveActionList.remove( 0 ) ;
			}
		}
		return retMe ;
	}

	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}

	/**
	 * Return true if at least one more work item is to be accomplished.
	 * @return
	 */
	public boolean hasMoreWork()
	{
		boolean isEmpty = false ;
		synchronized( moveActionList )
		{
			isEmpty = moveActionList.isEmpty() ;
		}
		return !isEmpty ;
	}
}
