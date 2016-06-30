package ui.screen;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import model.GameState;
import util.Text;

/**
 * @author Sebastian Stern
 */
public class WinnerScreen extends UIScreen {

	private GameState gameState;

	private int team1Points;

	private int team2Points;

	public WinnerScreen(GameState gameState, int team1Points, int team2Points) {
		this.gameState = gameState;
		this.team1Points = team1Points;
		this.team2Points = team2Points;
	}

	@Override
	protected Node createUI() {
		Pane pane = new VBox();

		String winnerTeam = gameState.getTeam(team1Points > team2Points).getName();
		Label winner = new Label(winnerTeam);
		pane.getChildren().add(winner);

		Button next = new Button(Text.NEXT);
		next.setOnMouseClicked(event ->
			controller.winnerScreenDismissed()
		);
		pane.getChildren().add(next);

		return pane;
	}
}
