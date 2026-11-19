-- Reinitialisation volontaire des six tables metier pour la demonstration locale.
-- ATTENTION : supprime les donnees et remet les sequences a 1. Sauvegarder avant.
BEGIN;
TRUNCATE TABLE reservations, points_arret, trajets, vehicules, passagers, conducteurs RESTART IDENTITY;
CREATE TEMP TABLE demo_ids (kind text, label text, id bigint) ON COMMIT DROP;
DO $$
DECLARE
    person record;
    trip record;
    booking record;
    new_id bigint;
    driver_id bigint;
    car_id bigint;
    trip_id bigint;
    passenger_id bigint;
    from_id bigint;
    to_id bigint;
    start_time timestamp;
BEGIN
    FOR person IN SELECT * FROM (VALUES
        ('Hamid', 'Alami', '0612457890', 'MA-458271'),
        ('Omar', 'Bennani', '0663728194', 'MA-619385'),
        ('Yassine', 'Idrissi', '0674839201', 'MA-723064'),
        ('Ahmed', 'Amrani', '0628591463', 'MA-384920'),
        ('Rachid', 'Fassi', '0659173284', 'MA-892156')
    ) AS people(first_name, last_name, phone, license) LOOP
        INSERT INTO conducteurs(first_name,last_name,email,phone,license_number)
        VALUES(person.first_name,person.last_name,lower(person.first_name || '.' || person.last_name) || '@example.com',
               person.phone,person.license) RETURNING id INTO new_id;
        INSERT INTO demo_ids VALUES('conducteur',person.first_name,new_id);
    END LOOP;
    FOR person IN SELECT * FROM (VALUES
        ('Salma', 'Benali', '0618932476'), ('Karim', 'El Mansouri', '0665283197'),
        ('Imane', 'Chakir', '0671928354'), ('Sara', 'Zahra', '0623749185'),
        ('Amine', 'Ait Ali', '0654827391')
    ) AS people(first_name,last_name,phone) LOOP
        INSERT INTO passagers(first_name,last_name,email,phone)
        VALUES(person.first_name,person.last_name,lower(replace(person.first_name || '.' || person.last_name,' ','')) || '@example.com',person.phone)
        RETURNING id INTO new_id;
        INSERT INTO demo_ids VALUES('passager',person.first_name,new_id);
    END LOOP;
    FOR person IN SELECT * FROM (VALUES
        ('Hamid','Dacia','Logan','48321-A-1','Blanc',4),
        ('Omar','Renault','Clio','27584-B-6','Bleu',4),
        ('Yassine','Toyota','Corolla','61937-A-8','Gris',4),
        ('Ahmed','Hyundai','Tucson','35816-D-1','Noir',5),
        ('Rachid','Peugeot','208','72469-B-6','Rouge',3)
    ) AS cars(owner,brand,model,registration,color,capacity) LOOP
        SELECT id INTO driver_id FROM demo_ids WHERE kind='conducteur' AND label=person.owner;
        INSERT INTO vehicules(brand,model,registration_number,color,capacity,conducteur_id)
        VALUES(person.brand,person.model,person.registration,person.color,person.capacity,driver_id)
        RETURNING id INTO new_id;
        INSERT INTO demo_ids VALUES('vehicule',person.owner,new_id);
    END LOOP;
    SELECT id INTO driver_id FROM demo_ids WHERE kind='conducteur' AND label='Hamid';
    SELECT id INTO car_id FROM demo_ids WHERE kind='vehicule' AND label='Hamid';
    INSERT INTO vehicules(brand,model,registration_number,color,capacity,conducteur_id)
    VALUES('Volkswagen','Polo','91352-A-1','Argent',4,driver_id) RETURNING id INTO new_id;
    INSERT INTO demo_ids VALUES('vehicule','Suppression',new_id);

    FOR trip IN SELECT * FROM (VALUES
        ('Reservation', 'futur', 'PUBLIE'),
        ('Terminaison', 'passe', 'PUBLIE'),
        ('ScenarioPrincipal', 'futur', 'PUBLIE'),
        ('ScenarioKarim', 'futur', 'COMPLET'),
        ('ScenarioDelai', 'proche', 'PUBLIE'),
        ('ScenarioAnnulation', 'futur', 'COMPLET')
    ) AS trips(label, moment, status) LOOP
        start_time := CASE trip.moment
            WHEN 'passe' THEN LOCALTIMESTAMP - INTERVAL '1 hour'
            WHEN 'proche' THEN LOCALTIMESTAMP + INTERVAL '2 hours'
            ELSE CURRENT_DATE + INTERVAL '3 days 17 hours' END;
        INSERT INTO trajets(departure_city,arrival_city,departure_time,offered_seats,status,conducteur_id,vehicule_id)
        VALUES('Rabat','Marrakech',start_time,3,trip.status,driver_id,car_id) RETURNING id INTO trip_id;
        INSERT INTO demo_ids VALUES('trajet',trip.label,trip_id);
        INSERT INTO points_arret(city,stop_order,passage_time,price_from_departure,trajet_id) VALUES
            ('Rabat',0,start_time,0,trip_id),
            ('Casablanca',1,start_time + INTERVAL '75 minutes',40,trip_id),
            ('Settat',2,start_time + INTERVAL '135 minutes',70,trip_id),
            ('Marrakech',3,start_time + INTERVAL '270 minutes',120,trip_id);
    END LOOP;
    FOR booking IN SELECT * FROM (VALUES
        ('Reservation','Salma',1,2,'ReservationSalma'),
        ('Terminaison','Salma',1,2,'TerminaisonSalma'),
        ('ScenarioKarim','Salma',1,2,'KarimSalma'),
        ('ScenarioKarim','Karim',2,1,'KarimKarim'),
        ('ScenarioDelai','Salma',1,2,'DelaiSalma'),
        ('ScenarioAnnulation','Salma',1,2,'AnnulationSalma'),
        ('ScenarioAnnulation','Karim',2,1,'AnnulationKarim')
    ) AS bookings(trip_label,passenger_name,seats,from_order,label) LOOP
        SELECT id INTO trip_id FROM demo_ids WHERE kind='trajet' AND label=booking.trip_label;
        SELECT id INTO passenger_id FROM demo_ids WHERE kind='passager' AND label=booking.passenger_name;
        SELECT id INTO from_id FROM points_arret WHERE trajet_id=trip_id AND stop_order=booking.from_order;
        SELECT id INTO to_id FROM points_arret WHERE trajet_id=trip_id AND stop_order=3;
        INSERT INTO reservations(date_reservation,montant_total,nombre_places,statut,descente_id,montee_id,passager_id,trajet_id)
        SELECT LOCALTIMESTAMP - INTERVAL '4 days',
               (d.price_from_departure-m.price_from_departure)*booking.seats,
               booking.seats,'CONFIRMEE',to_id,from_id,passenger_id,trip_id
        FROM points_arret m,points_arret d WHERE m.id=from_id AND d.id=to_id
        RETURNING id INTO new_id;
        INSERT INTO demo_ids VALUES('reservation',booking.label,new_id);
    END LOOP;
END $$;
SELECT jsonb_build_object(
    'ids',(SELECT jsonb_agg(to_jsonb(d) ORDER BY kind,label) FROM demo_ids d),
    'points',(SELECT jsonb_agg(to_jsonb(p) ORDER BY p.trajet_id,p.stop_order)
              FROM points_arret p WHERE p.trajet_id IN (SELECT id FROM demo_ids WHERE kind='trajet')),
    'datePreparation',LOCALTIMESTAMP
);
COMMIT;
