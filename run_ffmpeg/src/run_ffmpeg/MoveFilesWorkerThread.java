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

				if( !Files.exists( moveInfo.getSourceFileDirectoryPath() ) )
				{
					log.info( getName() + " Creating sourceFileDirectoryPath: " + moveInfo.getSourceFileDirectoryPath()
					+ " " + toString() ) ;
					if( !common.getTestMode() )
					{
						Files.createDirectory( moveInfo.getSourceFileDirectoryPath() ) ;
					}
				}

				if( !Files.exists( moveInfo.getDestinationFileDirectoryPath() ) )
				{
					log.info( getName() + " Creating destinationFileDirectoryPath: " + moveInfo.getDestinationFileDirectoryPath()
					+ " " + toString() ) ;
					if( !common.getTestMode())
					{
						Files.createDirectory( moveInfo.getDestinationFileDirectoryPath() ) ;
					}
				}

				log.info( getName() + " Moving " + moveInfo.getSourceFilePath().toString()
						+ " -> " + moveInfo.getDestinationFilePath().toString() ) ;

				Path temp = null ;
				final long startTime = System.nanoTime() ;
				if( common.isDoMoveFiles() )
				{
					temp = Files.move(	moveInfo.getSourceFilePath(), moveInfo.getDestinationFilePath() ) ;
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
			}
			catch( Exception theException )
			{
				log.warning( getName() + " Exception: " + theException.toString() ) ;
			}
		} // while( keepRunning )
	}

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
