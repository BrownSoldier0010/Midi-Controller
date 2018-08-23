package murphystudio.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Line;
import murphystudio.models.MainModel;
import murphystudio.objects.Accord;
import murphystudio.objects.TimelineElement;

import javax.sound.midi.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class PisteController extends Controller {

    /**
     *
     * TrackNotes is a list of the notes currently in the piste
     */
    public HashMap<Integer, ArrayList<Accord>> trackNotes = new HashMap<>();
    public ArrayList<Accord> empty = new ArrayList<>();
    @FXML
    public TextField piste_name_input;
    @FXML
    public Line headLine;
    @FXML
    public Slider piste_volume_slider;
    @FXML
    public ImageView piste_instrument_icon;
    @FXML
    public ChoiceBox<String> piste_instrument_selection;
    @FXML
    public ScrollPane scrollpane;
    @FXML
    public Button deletePistePtn;
    public Button recordPisteBtn;
    private ChordSorterController controller;
    public Track track;
    public AnchorPane timeline;
    public Button playBtn;

    private boolean isRecording;

    public Sequencer sequencer;
    public Sequence sequence;

    private double end;

    private ArrayList<TimelineElement> chords;
    private boolean isPlaying = false;
    private Integer instrument;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.end = 0.0;
        this.chords = new ArrayList<>();
        /*
            /!\ ATTENTION /!\
            Ici le controller n'est pas encore chargé.
            La méthode initialize est appelée lorsque l'on fait FXMLLoader.load(); (CF murphystudio.application.Controller - @loadView() )
         */
        /* Tout ce qui agit sur le fxml, tu le code ici */
    }

    private void initAll() {
        this.deletePistePtn.setOnMouseClicked(event -> {
            this.model.pisteLayoutController.removePiste(this);
        });

        this.playBtn.setOnMouseClicked(event -> {
            if (this.sequence != null) {
                if (!this.isPlaying)
                    this.play();
                else
                    this.stop();

            }
        });
        for (Map.Entry<String, Integer> instrument : this.model.intrumentsMIDI.entrySet()) {
            this.piste_instrument_selection.getItems().add(instrument.getKey());
        }

        this.piste_instrument_selection.getSelectionModel().select("Choir");

        recordPisteBtn.setOnMouseClicked(event -> {
            if (this.model.mainExternInterface.sequencer.isRecording()) {
                this.sequence = this.model.mainExternInterface.stopRecording();
                recordPisteBtn.setText("○");
            } else {
                if (this.model.mainExternInterface.MidiInput != null && this.model.mainExternInterface.MidiOutput != null) {
                    this.model.mainExternInterface.startRecording();
                    recordPisteBtn.setText("■");
                }
            }
        });

    }

    public void setName(String name) {
        piste_name_input.setText(name);
    }

    public void setModel(MainModel model) {
        this.model = model;
        initAll();
    }

    public void addChords(double length) {
        if (length <= 0) {
            return;
        }
        TimelineElement new_e = new TimelineElement(this.end, length * 4);
        this.chords.add(new_e);
        this.timeline.getChildren().add(new_e);
        ContextMenu rightClickContext = new ContextMenu();
        rightClickContext.getStyleClass().add("background");
        MenuItem menuItemDelete = new MenuItem("Delete");
        rightClickContext.getItems().add(menuItemDelete);
        menuItemDelete.setOnAction(MouseEvent -> this.removeChords(new_e));
        new_e.setOnContextMenuRequested(contextMenuEvent -> rightClickContext.show(this.timeline, contextMenuEvent.getScreenX(), contextMenuEvent.getScreenY()));
        updateEnd();
    }

    /**
     * need to collect current notes in track
     * remove the note at the index in the storage was thinking a Map<int,Chord>
     * rebuild the track with the notes left
     *
     * @param chords
     */
    private void removeChords(TimelineElement chords) {
        removeNote(chords);
        recreateTimeline();
        updateEnd();
    }

    /**
     * Removes the Note from all arrays
     * @param chords
     */
    private void removeNote(TimelineElement chords) {
        this.timeline.getChildren().remove(chords);
        this.trackNotes.remove(this.chords.indexOf(chords));
        System.out.println(this.trackNotes.size());
        System.out.println(this.trackNotes.keySet());
        this.chords.remove(chords);
    }

    /**
     * Recreates the TimeLine event with the notes in trackNotes
     * adds it to the Sequence on the piste
     */
    private void recreateTimeline() {
        this.sequence = null;
        for (int i : this.trackNotes.keySet()) {
            this.addSequence(this.model.midiInterface.createTrackFromChords(this.trackNotes.get(i)));
        }
    }


    private void updateEnd() {
        this.end = 0.0;
        for (TimelineElement e : this.chords) {
            if (e.getEnd() > this.end) {
                this.end = e.getEnd();
            }
        }
        this.end++;
    }

    public String toString() {
        return this.piste_name_input.getCharacters().toString();
    }

    public void play() {
        try {
            this.sequencer = MidiSystem.getSequencer();
            this.sequencer.open();

            this.instrument = this.model.intrumentsMIDI.get(this.piste_instrument_selection.getSelectionModel().getSelectedItem());

            this.playBtn.setText("Pause");

            this.sequence = model.midiInterface.cropSequence(this.sequence, 0, 0);

            this.sequence = this.model.midiInterface.setInstrument(this.sequence, instrument);

            this.sequencer.setSequence(this.sequence);
            this.sequencer.setTempoInBPM(this.model.midiInterface.tempo);
            this.sequencer.setLoopCount(0);
            this.sequencer.start();
            this.isPlaying = true;
        } catch (MidiUnavailableException | InvalidMidiDataException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (this.sequencer == null || this.sequence == null) return;
        this.playBtn.setText("Play");
        this.isPlaying = false;
        this.sequencer.stop();
        this.sequencer.close();
    }

    public void addSequence(Sequence trackFromChords) {
        this.instrument = this.model.intrumentsMIDI.get(this.piste_instrument_selection.getSelectionModel().getSelectedItem());

        if (this.sequence == null)
            this.sequence = this.model.midiInterface.mergeSequence(trackFromChords, null, instrument);
        else {
            this.sequence = this.model.midiInterface.mergeSequence(
                    this.sequence,
                    trackFromChords,
                    instrument
            );
        }
    }

    public void setDisplayName(ActionEvent actionEvent) {
        this.setName(actionEvent.getSource().toString());
    }
}
