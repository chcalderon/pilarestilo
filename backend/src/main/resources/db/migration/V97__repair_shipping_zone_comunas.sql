-- LOCAL only ever had 4 of the 10 comunas that actually make up the Aconcagua valley (Provincia
-- de Los Andes + Provincia de San Felipe de Aconcagua), and REGIONAL shipped with an empty comuna
-- list since V42 despite being named "V Region y RM" -- so nothing could ever match it. Both gaps
-- meant a shipping-zone auto-detection feature would have nothing correct to detect against.
--
-- REGIONAL is retitled here too: the owner confirmed it means the rest of the Valparaiso region
-- only, not Santiago/RM as the old title claimed -- Region Metropolitana's ~52 comunas now fall
-- through to NACIONAL along with the rest of the country, which is also why NACIONAL keeps an
-- empty comuna list on purpose: it is the implicit "everything not explicitly LOCAL or REGIONAL"
-- zone, not a third list to keep in sync by hand.
--
-- Isla de Pascua is part of the Valparaiso region administratively but is reached by plane, not a
-- regular courier -- left out of REGIONAL's list on purpose, so it falls to NACIONAL rather than
-- promising a "2-4 dias habiles" ETA a truck cannot deliver on.
--
-- Source of truth for both lists: this database's own geo_communes seed (V49), not an external
-- list, so the zones can only ever disagree with what a customer's address form actually offers.
--
-- jsonb `||` merges just the given keys into each zone object; everything else (etaEs/etaEn,
-- active, sortOrder, and the untouched NACIONAL object) survives unless a real admin edit already
-- changed it -- this repairs the known-wrong seed, it does not blindly replace the whole column.

UPDATE system_settings
SET shipping_zones_json = (
    SELECT jsonb_agg(
        CASE
            WHEN zone ->> 'code' = 'LOCAL' THEN
                zone || jsonb_build_object(
                    'comunas', '["Calle Larga","Catemu","Llay-Llay","Los Andes","Panquehue","Putaendo","Rinconada","San Esteban","San Felipe","Santa María"]'::jsonb
                )
            WHEN zone ->> 'code' = 'REGIONAL' THEN
                zone || jsonb_build_object(
                    'titleEs', 'Región de Valparaíso',
                    'titleEn', 'Valparaíso Region',
                    'comunas', '["Algarrobo","Cabildo","Cartagena","Casablanca","Concón","El Quisco","El Tabo","Hijuelas","Juan Fernández","La Calera","La Cruz","La Ligua","Limache","Nogales","Olmué","Papudo","Petorca","Puchuncaví","Quillota","Quilpué","Quintero","San Antonio","Santo Domingo","Valparaíso","Villa Alemana","Viña del Mar","Zapallar"]'::jsonb
                )
            ELSE zone
        END
        ORDER BY ordinality
    )::text
    FROM jsonb_array_elements(shipping_zones_json::jsonb) WITH ORDINALITY AS t(zone, ordinality)
)
WHERE id = 1 AND shipping_zones_json IS NOT NULL;
