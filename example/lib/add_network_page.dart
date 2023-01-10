import 'package:auto_connect_example/add_network_cubit.dart';
import 'package:auto_connect_example/forget_network_page.dart';
import 'package:auto_connect_example/place.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_typeahead/flutter_typeahead.dart';

class AddNetworkPage extends StatefulWidget {
  final String? wifiName;
  final String? placeName;
  const AddNetworkPage({super.key, this.wifiName, this.placeName});

  @override
  State<StatefulWidget> createState() => _AddNetworkPageState();
}

class _AddNetworkPageState extends State<AddNetworkPage> {
  GlobalKey<FormState> formKey = GlobalKey<FormState>();
  String? ssid;
  String? password;
  Place? place;

  final _passwordController = TextEditingController();
  bool _isPasswordRequired = false;

  final _placeController = TextEditingController();
  bool _isPlaceSelected = false;
  Place? _selectedPlace;

  void updateSsid(String ssid) {
    this.ssid = ssid;
  }

  void updatePassword(String password) {
    this.password = password;
  }

  void updatePlace(Place place) {
    this.place = place;
  }

  void saveNetwork() {
    context.read<AddNetworkCubit>().addNetwork(ssid!, password);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        padding: const EdgeInsets.only(
          left: 8.0,
          right: 8.0,
          top: 4.0,
        ),
        child: Form(
          key: formKey,
          child: Column(
            children: [
              _addNetworkTitle(),
              _ssidRow(),
              _passwordRow(),
              _placeRow(),
              _submitNetworkButton(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _addNetworkTitle() {
    return Container(
      padding: const EdgeInsets.only(top: 8.0, bottom: 16.0),
      child: Text(
        "Add Network",
        style: Theme.of(context).textTheme.headlineSmall,
      ),
    );
  }

  Widget _ssidRow() {
    return Row(
      children: [
        Expanded(
          child: TextFormField(
            decoration: const InputDecoration(
              border: OutlineInputBorder(),
              labelText: "Network name",
              contentPadding: EdgeInsets.symmetric(
                vertical: 2.0,
                horizontal: 8.0,
              ),
            ),
            style: Theme.of(context).textTheme.bodyMedium,
            enabled: false,
            initialValue: widget.wifiName,
            validator: (value) {
              if (value == null || value.isEmpty || value != widget.wifiName) {
                debugPrint("Invalid network");
                return "Invalid network";
              }
              return null;
            },
            onSaved: (value) {
              assert(value != null);
              setState(() => ssid = value);
            },
          ),
        ),
      ],
    );
  }

  Widget _passwordRow() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        children: [
          Expanded(
            child: _passwordTextFormField(),
          ),
          Container(
            padding: const EdgeInsets.symmetric(
              vertical: 16.0,
              horizontal: 16.0,
            ),
            child: Column(
              children: [
                Text(
                  "Password\nrequired?",
                  maxLines: 2,
                  style: Theme.of(context).textTheme.caption,
                  textAlign: TextAlign.center,
                ),
                Switch(
                  activeColor: Theme.of(context).colorScheme.primary,
                  value: _isPasswordRequired,
                  onChanged: (value) {
                    if (value == false) {
                      _passwordController.clear();
                    }
                    setState(() {
                      _isPasswordRequired = value;
                    });
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _passwordTextFormField() {
    return Row(
      children: [
        Expanded(
          child: TextFormField(
            controller: _passwordController,
            decoration: const InputDecoration(
              border: OutlineInputBorder(),
              labelText: "Password",
              contentPadding: EdgeInsets.symmetric(
                vertical: 2.0,
                horizontal: 8.0,
              ),
            ),
            style: Theme.of(context).textTheme.bodyMedium,
            enabled: _isPasswordRequired,
            onChanged: (text) => setState(() {}),
            onSaved: (value) {
              if (value != null) {
                setState(() => password = value);
              }
            },
          ),
        ),
      ],
    );
  }

  Widget _placeRow() {
    return Row(
      children: [
        Expanded(
          child: (widget.placeName == null)
              ? Container(
                  padding: const EdgeInsets.symmetric(vertical: 4.0),
                  child: _searchPlaceTypeAheadField(),
                )
              : _fixedPlaceTextField(widget.placeName!),
        ),
      ],
    );
  }

  Widget _searchPlaceTypeAheadField() {
    return TypeAheadFormField<Place>(
      textFieldConfiguration: TextFieldConfiguration(
        controller: _placeController,
        decoration: const InputDecoration(
          border: OutlineInputBorder(),
          labelText: "Location",
          contentPadding: EdgeInsets.symmetric(
            vertical: 2.0,
            horizontal: 8.0,
          ),
        ),
        style: Theme.of(context).textTheme.bodyMedium,
      ),
      onSuggestionSelected: (place) {
        _placeController.text = place.name;
        setState(() {
          _isPlaceSelected = true;
          _selectedPlace = place;
        });
      },
      itemBuilder: (BuildContext context, Place place) {
        return ListTile(
          title: Text(
            place.name,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        );
      },
      errorBuilder: (context, error) {
        return Text(error.toString());
      },
      suggestionsCallback: (query) {
        return <Place>[
          const Place(placeId: "1", name: "Place 1"),
          const Place(placeId: "2", name: "Place 2"),
          const Place(placeId: "3", name: "Place 3"),
        ];
      },
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null ||
            value.isEmpty ||
            !_isPlaceSelected ||
            value != _selectedPlace?.name) {
          debugPrint("Invalid place");
          return "Invalid place";
        }
        return null;
      },
      onSaved: (value) {
        assert(_selectedPlace != null);
        setState(() => place = _selectedPlace!);
      },
    );
  }

  Widget _fixedPlaceTextField(String placeName) {
    return TextFormField(
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        labelText: "Location",
      ),
      style: Theme.of(context).textTheme.bodyMedium,
      enabled: false,
      initialValue: widget.placeName,
      validator: (value) {
        if (value == null || value.isEmpty || value != placeName) {
          debugPrint("Invalid place");
          return "Invalid place";
        }
        return null;
      },
    );
  }

  Widget _submitNetworkButton() {
    final button = ElevatedButton(
      onPressed: () async {
        final valid = formKey.currentState!.validate();
        if (valid) {
          formKey.currentState!.save();
          saveNetwork();
          Navigator.of(context).push(
            MaterialPageRoute(
              builder: (context) => const ForgetNetworkPage(),
            ),
          );
        }
      },
      child: Text(
        "Submit Network",
        style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: Theme.of(context).colorScheme.onPrimary,
            ),
      ),
    );
    return button;
  }
}
