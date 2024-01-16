package run_ffmpeg;

import java.util.logging.Logger;

public class MoveFilesControllerWrapper extends TranscodeAndMoveFilesWorkerThread
{
	private transient TranscodeAndMoveFiles theController = null ;
	private MoveFiles moveFilesController = null ;
	
	public MoveFilesControllerWrapper( TranscodeAndMoveFiles theController,
			MoveFiles moveFilesController,
			Logger log,
			Common common )
	{
		super( log, common ) ;
		assert( theController != null ) ;
		assert( moveFilesController != null ) ;
		
		this.theController = theController ;
		this.moveFilesController = moveFilesController ;
	}
	
	@Override
	public void run()
	{
		moveFilesController.Init() ;
		moveFilesController.Execute() ;

		// No longer running.
		// Shutdown the MoveFiles controller, so it can shutdown its threads as well.
//		moveFilesController.stopRunning() ;
	}
	
	@Override
	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}
	
}
