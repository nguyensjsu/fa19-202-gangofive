import javax.swing.JOptionPane;
import javax.swing.JInternalFrame;

public class Level1 implements Ilevel {

	LevelController controller;
	int obstacleTime;
	int powerUpTime;
	int speed;
	public Level1(LevelController controller) {
		this.obstacleTime = 4;
		this.powerUpTime = 7;
		this.controller = controller;
		this.speed = 8;
	}

	@Override
	public void changeState() {

		controller.changeToLevel2();
	}

	@Override
	public int getObstacleTime() {

		return obstacleTime;
	}

	@Override
	public int getPowerUpTime() {
		return powerUpTime;

	}
	public int getCurrentSpeed(){
		return speed;
	}
}
